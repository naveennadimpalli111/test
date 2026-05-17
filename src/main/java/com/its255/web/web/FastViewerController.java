package com.its255.web.web;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.its255.schema.SchemaRegistry;
import com.its255.util.LoggingUtil;
import com.its255.viewer.ChunkedMMapRecordStore;
import com.its255.viewer.FastRecordFilter;
import com.its255.viewer.ParallelPrefixIndexBuilder;
import com.its255.viewer.RecordNavigator;
import com.its255.viewer.RecordQuery;
import com.its255.viewer.SchemaHtmlRenderer;
import com.its255.viewer.ViewerConfig;
import com.its255.viewer.ViewerSession;
import com.its255.web.cleanup.CleanupScheduler;
import com.its255.web.service.CsvExportService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/viewer")
public class FastViewerController {
	private final ViewerConfig cfg;
	private final CleanupScheduler cleanupScheduler;

	public FastViewerController(ViewerConfig cfg, CleanupScheduler cleanupScheduler) {
		this.cfg = cfg;
		this.cleanupScheduler = cleanupScheduler;
	}

	private Charset cs() {
		return Charset.forName(cfg.getCharset());
	}

	@GetMapping
	public String page(Model model, HttpSession session) {
		ViewerSession vs = getSession(session);

		model.addAttribute("hasFile", vs.hasFile());
		model.addAttribute("count", (vs.nav != null) ? vs.nav.size() : 0);
		model.addAttribute("position", (vs.nav != null) ? vs.nav.position() : 0);
		model.addAttribute("hasPrev", vs.nav != null && vs.nav.hasPrev());
		model.addAttribute("hasNext", vs.nav != null && vs.nav.hasNext());
		model.addAttribute("progress", vs.progress);

		// NEW: show uploaded filename
		model.addAttribute("filename", vs.originalFilename);
		model.addAttribute("html", "");

		return "viewer";
	}

	@ResponseBody
	@GetMapping("/progress")
	public Map<String, Object> progress(HttpSession session) {

		ViewerSession vs = getSession(session);
		return Map.of("progress", vs.progress);

	}

	@PostMapping("/upload")
	public String upload(@RequestParam("file") MultipartFile file,
			@RequestParam("transactionType") String transactionType, Model model, HttpSession session)
			throws Exception {
		if (file.isEmpty()) {
			model.addAttribute("error", "Please select a file.");
			return page(model, session);
		}
		if (transactionType == null || transactionType.isBlank()) {
			throw new IllegalStateException("Transaction type missing in ViewerSession. Upload required.");
		}
		ViewerSession vs = getSession(session);
		vs.transactionType = transactionType;

		// clean old store if present
		if (vs.store instanceof AutoCloseable ac) {
			try {
				ac.close();
			} catch (Exception ignore) {
			}
		}

		// create app-specific temp root
		Path appTmpRoot = getAppTempRoot();

		// create per-session directory
		Path sessionDir = Files.createTempDirectory(appTmpRoot, "session_");

		// store uploaded file inside session directory
		Path uploadedFile = sessionDir.resolve("uploaded.dat");

		file.transferTo(uploadedFile.toFile());

		// save paths in session
		vs.sessionDir = sessionDir;
		vs.filePath = uploadedFile;
		vs.originalFilename = file.getOriginalFilename();
		vs.progress = 0.0;

		long windowBytes = Math.max(1, cfg.getWindowSizeMB()) * 1024L * 1024L;

		int recordLength = SchemaRegistry.getRecordLength(transactionType);

		vs.store = new ChunkedMMapRecordStore(uploadedFile, cs(), recordLength, windowBytes, transactionType);

		// TEMP debug print (remove later)
		LoggingUtil.debug("Viewer upload: sessionDir=" + sessionDir + ", filePath=" + uploadedFile);

		int workers = Math.min(cfg.getIndexWorkers(), (int) getRecordCount(vs.store));
		ParallelPrefixIndexBuilder builder = new ParallelPrefixIndexBuilder(vs.store, cfg.getPrefixIndexLength(),
				workers, cfg.getProgressStep(), p -> vs.progress = p);

		// If you still build index synchronously:
		vs.pidx = builder.build();
		vs.filter = new FastRecordFilter(vs.store, vs.pidx);
		vs.nav = new RecordNavigator(List.of(), -1);
		vs.lastFiltered = List.of();

		return "redirect:/viewer";
	}

	@PostMapping("/search")
	public String search(@RequestParam(name = "sccf", required = false) String sccf,
			@RequestParam(name = "recordTypeCode", required = false) String type,
			@RequestParam(name = "recNo", required = false) Integer recNo, Model model, HttpSession session) {
		ViewerSession vs = getSession(session);

		if (!vs.hasFile()) {
			model.addAttribute("error", "Upload a file first.");
			return page(model, session);
		}

		var q = new RecordQuery(Optional.ofNullable((sccf != null && !sccf.isBlank()) ? sccf.trim() : null),
				Optional.ofNullable((type != null && !type.isBlank()) ? type.trim() : null),
				Optional.ofNullable(recNo));

		List<Integer> filtered = vs.filter.apply(q);
		vs.selectedRecordType = type;
		int startRn = q.recordNumber().orElse(-1);

		vs.nav = new RecordNavigator(filtered, startRn);
		vs.lastFiltered = filtered;

		return "redirect:/viewer/current";

	}

	@GetMapping("/current")
	public String current(Model model, HttpSession session) {
		ViewerSession vs = getSession(session);
		String viewMode = (String) session.getAttribute("viewMode");
		model.addAttribute("viewMode", viewMode);

		model.addAttribute("editMode", Boolean.TRUE.equals(vs.editMode));

		if (!vs.hasFile() || vs.nav == null || vs.nav.size() == 0) {
			model.addAttribute("html", "<div class='alert alert-info'>No results. Upload a file and search.</div>");
			return page(model, session);
		}

		int rn = vs.nav.currentRecordNumber();

		String type = safeInvoke(vs.store, "readType", rn);
		String sccf = safeInvoke(vs.store, "readSccf", rn);
		String txn = safeInvoke(vs.store, "readTxn", rn);
		SchemaHtmlRenderer renderer = new SchemaHtmlRenderer(vs.store, cs(), vs.transactionType, vs.editMode,
				vs.editOverlay);
		String tableHtml = renderer.render(rn);

		Map<String, String> recordOverlay = vs.editOverlay.get(rn);
		if (recordOverlay != null) {
			sccf = recordOverlay.getOrDefault("SCCF", sccf);
			String localPlan = recordOverlay.get("FM105-SER-NUM-LOCAL-PLAN");
			String cc = recordOverlay.get("FM105-SER-NUM-JULDT-CC");
			String yy = recordOverlay.get("FM105-SER-NUM-JULDT-YY");
			String ddd = recordOverlay.get("FM105-SER-NUM-JULDT-DDD");
			String sequence = recordOverlay.get("FM105-SER-NUM-SEQUENCE");
			String suffix = recordOverlay.get("FM105-SER-NUM-SUFFIX");
			if (localPlan != null && cc != null && ddd != null && sequence != null && suffix != null) {
				sccf = localPlan + cc + yy + ddd + sequence + suffix;
			}
			type = recordOverlay.getOrDefault("REC_TYPE", type);
			type = recordOverlay.getOrDefault("FM105-REC-TYPE", type);
		}

		StringBuilder sb = new StringBuilder();
		sb.append("<h5 id='record-context'>").append("Record #").append(rn).append(" SCCF=").append(sccf)
				.append(" Type=").append(type.trim()).append("</h5>\n").append(tableHtml).append("<pre>")
				.append("Record #").append(rn).append(" SCCF=").append(sccf).append(" Type=").append(type.trim())
				.append("</pre>\n");

		model.addAttribute("hasFile", true);
		model.addAttribute("filename", vs.originalFilename); // NEW
		model.addAttribute("count", vs.nav.size());
		model.addAttribute("position", vs.nav.position());
		model.addAttribute("hasPrev", vs.nav.hasPrev());
		model.addAttribute("hasNext", vs.nav.hasNext());
		model.addAttribute("html", sb.toString());
		model.addAttribute("editMode", vs.editMode);
		/*
		 * model.addAttribute("verticalHtml",renderer.renderVertical(vs.nav.position()))
		 * ;
		 */
		/* model.addAttribute("horizontalHtml",renderer.renderHorizontal()); */
		model.addAttribute("verticalHtml", renderer.renderVertical(vs.nav.currentRecordNumber()));

		model.addAttribute("horizontalHtml",
				renderer.renderHorizontal(session, vs.selectedRecordType, vs.lastFiltered));
		if (vs.selectedRecordType != null) {
			model.addAttribute("horizontalHtml",
					renderer.renderHorizontal(session, vs.selectedRecordType, vs.lastFiltered));
		}

		/*
		 * model.addAttribute("horizontalHtml",renderer.renderHorizontal(session,vs.
		 * selectedRecordType,vs.lastFiltered));
		 */
		return "viewer";

	}

	@PostMapping("/prev")
	public String prev(HttpSession session) {
		ViewerSession vs = getSession(session);
		if (vs.nav != null && vs.nav.hasPrev())
			vs.nav.prev();
		return "redirect:/viewer/current";

	}

	@PostMapping("/next")
	public String next(HttpSession session) {
		ViewerSession vs = getSession(session);
		if (vs.nav != null && vs.nav.hasNext())
			vs.nav.next();
		return "redirect:/viewer/current";
	}

	@GetMapping("/export")
	public void exportZip(jakarta.servlet.http.HttpServletResponse resp, HttpSession session) throws Exception {
		ViewerSession vs = getSession(session);
		if (!vs.hasFile() || vs.lastFiltered == null || vs.lastFiltered.isEmpty())
			return;

		resp.setContentType("application/zip");
		resp.setHeader("Content-Disposition", "attachment; filename=its255_scope_export.zip");

		CsvExportService svc = new CsvExportService(vs.store, cs(), cfg.getExportBufferSize(), vs.transactionType);
		svc.exportPerTypeZip(vs.lastFiltered, resp.getOutputStream());

	}

	private ViewerSession getSession(HttpSession session) {
		ViewerSession vs = (ViewerSession) session.getAttribute("VIEWER_SESSION");
		if (vs == null) {
			vs = new ViewerSession();
			session.setAttribute("VIEWER_SESSION", vs);
		}
		return vs;
	}

	private String safeInvoke(Object store, String method, int rn) {
		try {
			return String.valueOf(store.getClass().getMethod(method, int.class).invoke(store, rn));
		} catch (Exception e) {
			return "";
		}
	}

	@GetMapping("/edit")
	public String enableEdit(HttpSession session) {
		ViewerSession vs = getSession(session);
		vs.editMode = true;
		return "redirect:/viewer/current";
	}

	@PostMapping("/save")

	public String save(HttpServletRequest request, HttpSession session) {
		ViewerSession vs = getSession(session);
		request.getParameterMap().forEach((key, value) -> {
			if (key.startsWith("field_")) {
				String[] parts = key.split("_", 3);
				int recordNo = Integer.parseInt(parts[1]);
				String fieldName = parts[2];
				String newValue = value[0];
				Map<String, String> recordEdits = vs.editOverlay.computeIfAbsent(recordNo, k -> new HashMap<>());
				recordEdits.put(fieldName, newValue);
				System.out.println("OVERLAY SAVED:record=" + recordNo + ",field=" + fieldName + ",value=" + newValue);
			}
		});
		vs.editMode = false;
		try {
			StringBuilder content = new StringBuilder();
			vs.editOverlay.forEach((recordNo, fields) -> {
				content.append("Record").append(recordNo).append("\n");
				fields.forEach((field, value) -> {
					content.append(field).append("=").append(value).append("\n");
				});
				content.append("\n");
			});
			Path outputPath = Paths.get("C:/edited-files/edited-records.txt");
			Files.createDirectories(outputPath.getParent());
			Files.write(outputPath, content.toString().getBytes(StandardCharsets.UTF_8));
			System.out.println("LOCAL FILE SAVED:" + outputPath);

		} catch (Exception e) {
			e.printStackTrace();
		}
		String viewMode = request.getParameter("viewMode");
		session.setAttribute("viewMode", viewMode);
		return "redirect:/viewer/current";
	}

	@GetMapping("/view")
	public String disableEdit(HttpSession session) {
		ViewerSession vs = getSession(session);
		vs.editMode = false;
		return "redirect:/viewer/current";
	}

	/*
	 * @GetMapping("/viewer/loadMore")
	 * 
	 * @ResponseBody public String loadMore(HttpSession session, @RequestParam
	 * String selectedType, @RequestParam int offset,
	 * 
	 * @RequestParam int limit) { ViewerSession vs = getSession(session);
	 * SchemaHtmlRenderer renderer = new
	 * SchemaHtmlRenderer(vs.store,Charset.defaultCharset(), vs.transactionType,
	 * vs.editMode, vs.editOverlay); return renderer.renderVerticalPage(session,
	 * selectedType, offset, limit); }
	 */
	@GetMapping("/viewer/loadMore")
	@ResponseBody
	public String loadMore(
	        HttpSession session,
	        @RequestParam String selectedType,
	        @RequestParam int offset,
	        @RequestParam int limit,
	        @RequestParam String viewType) {

	    ViewerSession vs = getSession(session);

	    SchemaHtmlRenderer renderer =
	        new SchemaHtmlRenderer(
	            vs.store,
	            Charset.defaultCharset(),
	            vs.transactionType,
	            vs.editMode,
	            vs.editOverlay);

	    if ("horizontal".equalsIgnoreCase(viewType)) {
	        return renderer.renderHorizontalPage(session, selectedType, offset, limit);
	    } else {
	        return renderer.renderVerticalPage(session, selectedType, offset, limit);
	    }
	}

	@PostMapping("/clear")
	public String clear(HttpSession session) throws IOException {
		ViewerSession vs = (ViewerSession) session.getAttribute("VIEWER_SESSION");
		session.removeAttribute("VIEWER_SESSION");
		if (vs != null) {
			LoggingUtil.debug("Clear cache invoked. sessionDir=" + (vs != null ? vs.sessionDir : null));
			try {
				vs.close();
			} catch (Exception ignore) {
				LoggingUtil.error(ignore);
			}

			// 2. Explicitly break references (important on Windows)
			vs.store = null;
			vs.filter = null;
			vs.pidx = null;
			vs.nav = null;
			vs.lastFiltered = null;

			if (vs.sessionDir != null) {
				LoggingUtil.debug("PHASE2_CLEAR: deleting sessionDir=" + vs.sessionDir);

				Path sessionDir = vs.sessionDir;
				vs = null;
				cleanupScheduler.scheduleCleanup(sessionDir);
			}

		}

		return "redirect:/viewer";
	}

	private long getRecordCount(Object store) {
		try {
			return (long) store.getClass().getMethod("getRecordCount").invoke(store);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Path getAppTempRoot() throws IOException {
		Path root = Paths.get(System.getProperty("java.io.tmpdir"), "its255Files");
		Files.createDirectories(root);
		return root;
	}
}