package com.its255.viewer;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.its255.constants.FileViewerConstants;
import com.its255.schema.FieldSpec;
import com.its255.schema.RecordType;
import com.its255.schema.SchemaRegistry;

import jakarta.servlet.http.HttpSession;

/**
 * Renders ONE 255-byte record as an HTML table using the active copybook
 * schema. - Supports ALPHA / NUMERIC_TEXT via Cp037 slicing + trim of EBCDIC
 * spaces (0x40) - Supports PACKED_DECIMAL and BINARY via Fixed255Parser
 * (reflection)
 *
 * This class depends on the host app providing: com.its255.schema.{Schemas,
 * RecordType, FieldSpec} com.its255.io.Fixed255Parser with methods:
 * decodeComp3ToString(byte[], int start, int len, int scale)
 * decodeBinary(byte[], int start, int len, int scale)
 */
public class SchemaHtmlRenderer {
	private static final byte EBCDIC_SPACE = (byte) 0x40;

	private final Object store; // must expose: readRecordBytes(int), readType(int)
	private final Charset cs;
	private final String transactionType;
	private final boolean editMode;
	private final Map<Integer, Map<String, String>> editOverlay;

	public SchemaHtmlRenderer(Object store, Charset cs, String transactionType, boolean editMode,
			Map<Integer, Map<String, String>> editOverlay) {
		this.store = store;
		this.cs = cs;
		this.transactionType = transactionType;
		this.editMode = editMode;
		this.editOverlay = editOverlay;
	}

	public String renderVertical(int recordNo) {
		String type = readType(recordNo).trim();
		RecordType rt = RecordType.from(type);
		List<FieldSpec> layout = SchemaRegistry.getSchema(transactionType, rt.code);
		byte[] rec = readRecordBytes(recordNo);
		StringBuilder sb = new StringBuilder();
		/* sb.append("<div style='max-height:700px;overflow:auto;'>"); */
		sb.append("<table class='table table-sm table-striped").append("table-bordered table-hover'>");
		sb.append("<thead>");
		sb.append("<tr>");
		sb.append("<th style='width:35%;'>Field</th>");
		sb.append("<th>Value</th>");
		sb.append("</tr>");
		sb.append("</thead>");
		sb.append("<tbody>");
		if (layout != null) {
			for (FieldSpec f : layout) {
				int start = f.start1Based - 1;
				int len = f.lengthBytes;
				String val = "";
				switch (f.type) {
				case ALPHA:
				case NUMERIC_TEXT:
					val = sliceTrim(rec, start, len);
					break;
				case PACKED_DECIMAL:
					val = invokeFixed("decodeComp3ToString", rec, start, len, f.scale);
					break;
				default:
					val = "";
				}
				sb.append("<tr>");
				sb.append("<td>").append(escape(f.name)).append("</td>");
				sb.append("<td>").append(escape(val)).append("</tr>");
			}
		}
		sb.append("</tbody>");
		sb.append("</table>");
		sb.append("</div>");
		
		 return sb.toString(); 
	}
	/*
	 * public String renderVerticalPage( HttpSession session,String selectedType,int
	 * offset,int limit){ ViewerSession
	 * vs=(ViewerSession)session.getAttribute("VIEWER_SESSION"); StringBuilder
	 * sb=new StringBuilder(8192);
	 * 
	 * int total=vs.filteredRecords.size(); int end=Math.min(offset+limit, total);
	 * 
	 * int end=offset+limit;
	 * 
	 * for(int i=offset;i<end;i++) { sb.append(renderVertical(i)); } return
	 * sb.toString(); }
	 * 
	 */ 
	public String renderVerticalPage(
	        HttpSession session,
	        String selectedType,
	        int offset,
	        int limit) {

	    ViewerSession vs = (ViewerSession) session.getAttribute("VIEWER_SESSION");

	    List<?> records = vs.lastFiltered;

	    if (records == null || records.isEmpty()) {
	        return "";
	    }

	    int total = records.size();
	    int end = Math.min(offset + limit, total);
	    if(offset>=total) {
	    	return "";
	    }

	    StringBuilder sb = new StringBuilder(8192);

	    for (int i = offset; i < end; i++) {

	        int recordNo = Integer.parseInt(records.get(i).toString());

	        sb.append(renderVertical(recordNo));
	    }

	    return sb.toString();
	}

	public String renderHorizontal(HttpSession session, String selectedType, List<?> filteredRecords) {
		ViewerSession vs = (ViewerSession) session.getAttribute("VIEWER_SESSION");

		StringBuilder sb = new StringBuilder(8192);

		/* sb.append("<div style='max-height:700px;overflow:auto;'>"); */
		sb.append("<table class='table table-sm table-striped table-bordered table-hover' ")
				.append("style='min-width:2200px; border-top:3px solid black;'>");

		sb.append("<thead><tr>");
		sb.append("<th style='min-width:180px;  border-top:3px;'>Record No</th>");
		sb.append("<th style='min-width:180px;  border-top:3px;'>SCCF ID</th>");
		sb.append("<th style='min-width:120px;  border-top:3px;'>Type</th>");
		List<FieldSpec> headerLayout = SchemaRegistry.getSchema(transactionType,
				(selectedType != null && !selectedType.isEmpty()) ? selectedType : "05");

		if (headerLayout != null) {
			for (FieldSpec f : headerLayout) {
				if ("SCCF".equalsIgnoreCase(f.name) || "REC_TYPE".equalsIgnoreCase(f.name)) {
					continue;
				}

				sb.append("<th>").append(escape(f.name)).append("</th>");

			}
		}

		sb.append("</tr></thead><tbody id='horizontalTbody'>");

		if (filteredRecords == null || filteredRecords.isEmpty()) {
			sb.append("<tr>").append("<td colspan='10'>No data found</td>").append("</tr>");
		} else {
			for (Object obj : filteredRecords) {
				int recordNo=Integer.parseInt(obj.toString());

				byte[] rec = readRecordBytes(recordNo);
				String type = readType(recordNo).trim();

				RecordType rt = RecordType.from(type);
				List<FieldSpec> rowLayout = SchemaRegistry.getSchema(transactionType, rt.code);

				sb.append("<tr>");
				sb.append("<td>").append(recordNo).append("</td>");

				String sccf = sliceTrim(rec, 0, 15); // adjust if needed
				sb.append("<td>").append(escape(sccf)).append("</td>");

				sb.append("<td>").append(escape(type)).append("</td>");

				if (rowLayout != null) {
					for (FieldSpec f : rowLayout) {

						if ("SCCF".equalsIgnoreCase(f.name) || "REC_TYPE".equalsIgnoreCase(f.name)) {
							continue;
						}

						int start = f.start1Based - 1;
						int len = f.lengthBytes;

						String val = "";

						switch (f.type) {
						case ALPHA:
						case NUMERIC_TEXT:
							val = sliceTrim(rec, start, len);
							break;
						case PACKED_DECIMAL:
							val = invokeFixed("decodeComp3ToString", rec, start, len, f.scale);
							break;
						default:
							val = "";
						}
						String fieldKey = "field_" + recordNo + "_" + f.name;

						Map<String, String> recordOverlay = editOverlay != null ? editOverlay.get(recordNo) : null;

						if (recordOverlay != null && recordOverlay.containsKey(f.name)) {
							val = recordOverlay.get(f.name);
						}

						/* sb.append("<td>").append(escape(val)).append("</td>"); */
						boolean editable = editMode && !List.of("SCCF", "REC_TYPE").contains(f.name);

						sb.append("<td>");

						if (editMode) {
							sb.append("<input type='text'")
									.append(" class='form-control form-control-sm editable-field'")
									/*
									 * .append(" name='field_").append(recordNo).append("_").append(escape(f.name))
									 */

									.append(" name='field_").append(recordNo).append("_").append(f.name) // ✅ no escape
																											// here
									.append("' ")

									.append("' ").append(" value='").append(escape(val)).append("' ")
									.append(" maxlength='").append(f.lengthBytes).append("'/>");
						} else {
							sb.append(escape(val));
						}

						sb.append("</td>");

					}
				}

				sb.append("</tr>");
			}
		}

		sb.append("</tbody></table>");

		return sb.toString();
	}

	/*
	 * public String renderHorizontalPage( HttpSession session, String selectedType,
	 * int offset, int limit) {
	 * 
	 * ViewerSession vs = (ViewerSession) session.getAttribute("VIEWER_SESSION");
	 * 
	 * StringBuilder sb = new StringBuilder(8192);
	 * 
	 * List<?> records = vs.lastFiltered;
	 * 
	 * if (records == null || records.isEmpty()) { return ""; }
	 * 
	 * int total = records.size(); int end = Math.min(offset + limit, total);
	 * 
	 * for (int i = offset; i < end; i++) {
	 * 
	 * int recordNo = Integer.parseInt(records.get(i).toString());
	 * 
	 * byte[] rec = readRecordBytes(recordNo); String type =
	 * readType(recordNo).trim();
	 * 
	 * RecordType rt = RecordType.from(type); List<FieldSpec> layout =
	 * SchemaRegistry.getSchema(transactionType, rt.code);
	 * 
	 * ✅ add ONLY ROW (NOT full table again) sb.append("<tr>");
	 * 
	 * sb.append("<td>").append(recordNo).append("</td>");
	 * 
	 * String sccf = sliceTrim(rec, 0, 15);
	 * sb.append("<td>").append(escape(sccf)).append("</td>");
	 * 
	 * sb.append("<td>").append(escape(type)).append("</td>");
	 * 
	 * if (layout != null) { for (FieldSpec f : layout) {
	 * 
	 * if ("SCCF".equalsIgnoreCase(f.name) || "REC_TYPE".equalsIgnoreCase(f.name)) {
	 * continue; }
	 * 
	 * int start = f.start1Based - 1; int len = f.lengthBytes;
	 * 
	 * String val = "";
	 * 
	 * switch (f.type) { case ALPHA: case NUMERIC_TEXT: val = sliceTrim(rec, start,
	 * len); break; case PACKED_DECIMAL: val = invokeFixed("decodeComp3ToString",
	 * rec, start, len, f.scale); break; default: val = ""; }
	 * 
	 * Map<String, String> recordOverlay = editOverlay != null ?
	 * editOverlay.get(recordNo) : null;
	 * 
	 * if (recordOverlay != null && recordOverlay.containsKey(f.name)) { val =
	 * recordOverlay.get(f.name); }
	 * 
	 * sb.append("<td>");
	 * 
	 * if (editMode) { sb.append("<input type='text'")
	 * .append(" class='form-control form-control-sm editable-field'")
	 * .append(" name='field_").append(recordNo).append("_").append(f.name).append(
	 * "'") .append(" value='").append(escape(val)).append("'")
	 * .append(" maxlength='").append(f.lengthBytes).append("'/>"); } else {
	 * sb.append(escape(val)); }
	 * 
	 * sb.append("</td>"); } }
	 * 
	 * sb.append("</tr>"); }
	 * 
	 * return sb.toString(); }
	 * 
	 */
	
	/*
	 * public String renderHorizontalPage( HttpSession session, String selectedType,
	 * int offset, int limit) {
	 * 
	 * ViewerSession vs = (ViewerSession) session.getAttribute("VIEWER_SESSION");
	 * 
	 * List<?> records = vs.lastFiltered;
	 * 
	 * if (records == null || records.isEmpty()) { return ""; }
	 * 
	 * int total = records.size(); int end = Math.min(offset + limit, total);
	 * if(offset>=total) { return ""; }
	 * 
	 * StringBuilder sb = new StringBuilder(8192); if(offset==0){
	 * sb.append("<div style='max-height:700px;overflow:auto;'>"); sb.
	 * append("<table class='table table-sm table-striped table-bordered table-hover'"
	 * ) .append("style='min-width:2200px;border-top:3px solid black;'>");
	 * sb.append("<thead><tr>");
	 * sb.append("<th style='min-width:180px;border-top:3px;'>Record NO</th>");
	 * sb.append("<th style='min-width:180px;border-top:3px;'>SCCFID</th>");
	 * sb.append("<th style='min-width:120px;border-top:3px;'>Type</th>");
	 * List<FieldSpec> headerLayout =
	 * SchemaRegistry.getSchema(transactionType,(selectedType!=null &&
	 * !selectedType.isEmpty())?selectedType:"05");
	 * 
	 * if (headerLayout != null) { for (FieldSpec f : headerLayout) {
	 * 
	 * if ("SCCF".equalsIgnoreCase(f.name) || "REC_TYPE".equalsIgnoreCase(f.name))
	 * continue; sb.append("<th>").append(escape(f.name)).append("</th>"); } }
	 * sb.append("</tr></thead><tbody id='horizontalTbody'>"); } else{
	 * sb.append("<tbody>"); } for(int i=offset;i<end;i++) { int
	 * recordNo=Integer.parseInt(records.get(i).toString()); byte[]
	 * rec=readRecordBytes(recordNo); String type=readType(recordNo).trim();
	 * RecordType rt=RecordType.from(type);
	 * List<FieldSpec>layout=SchemaRegistry.getSchema(transactionType,rt.code);
	 * sb.append("<tr>"); sb.append("<td>").append(recordNo).append("</td>");
	 * sb.append("<td>").append(escape(sliceTrim(rec,0,15))).append("</td>");
	 * sb.append("<td>").append(escape(type)).append("</td>"); if(layout!=null) {
	 * for(FieldSpec f:layout) { if("SCCF".equalsIgnoreCase(f.name)||
	 * "REC_TYPE".equalsIgnoreCase(f.name))continue;
	 * 
	 * 
	 * int start = f.start1Based - 1; int len = f.lengthBytes; String val=""; switch
	 * (f.type) { case ALPHA: case NUMERIC_TEXT: val = sliceTrim(rec, start, len);
	 * break; case PACKED_DECIMAL: val = invokeFixed("decodeComp3ToString", rec,
	 * start, len, f.scale); break; default:val=""; }
	 * 
	 * sb.append("<td>").append(escape(val)).append("</td>"); } }
	 * sb.append("</tr>"); } sb.append("</tbody>"); if(offset==0) {
	 * sb.append("</table></div>");
	 * 
	 * }
	 * 
	 * return sb.toString(); }
	 */
	public String renderHorizontalPage(HttpSession session, String selectedType, int offset, int limit) {

	    ViewerSession vs = (ViewerSession) session.getAttribute("VIEWER_SESSION");
	    List<?> records = vs.lastFiltered;

	    if (records == null || records.isEmpty()) return "";

	    int total = records.size();
	    if (offset >= total) return "";

	    int end = Math.min(offset + limit, total);

	    StringBuilder sb = new StringBuilder();

	    // ✅ ONLY ROWS — NO <table>, NO <tbody>
	    for (int i = offset; i < end; i++) {

	        int recordNo = Integer.parseInt(records.get(i).toString());
	        byte[] rec = readRecordBytes(recordNo);
	        String type = readType(recordNo).trim();

	        RecordType rt = RecordType.from(type);
	        List<FieldSpec> layout = SchemaRegistry.getSchema(transactionType, rt.code);

	        sb.append("<tr>");
	        sb.append("<td>").append(recordNo).append("</td>");
	        sb.append("<td>").append(escape(sliceTrim(rec, 0, 15))).append("</td>");
	        sb.append("<td>").append(escape(type)).append("</td>");

	        if (layout != null) {
	            for (FieldSpec f : layout) {

	                if ("SCCF".equalsIgnoreCase(f.name) || "REC_TYPE".equalsIgnoreCase(f.name)) continue;

	                int start = f.start1Based - 1;
	                int len = f.lengthBytes;

	                String val = switch (f.type) {
	                    case ALPHA, NUMERIC_TEXT -> sliceTrim(rec, start, len);
	                    case PACKED_DECIMAL -> invokeFixed("decodeComp3ToString", rec, start, len, f.scale);
	                    default -> "";
	                };

	                sb.append("<td>").append(escape(val)).append("</td>");
	            }
	        }

	        sb.append("</tr>");
	    }

	    return sb.toString();
	}
	
	private int getRecordCount() {
		try {
			return (Integer) store.getClass().getMethod("recordCount").invoke(store);
		} catch (Exception e) {
			return 0;
		}
	}

	private String row(String name, String value, int recordNo, FieldSpec fieldSpec) {
		boolean editable = editMode && isEditableField(name);
		StringBuilder sb = new StringBuilder();
		sb.append("<tr>");
		sb.append("<th scope='row' style='white-space:nowrap'>").append(escape(name)).append("</th>");
		sb.append("<td>");
		if (editable) {
			sb.append("<input type='text'").append("class='form-control form-control-sm editable-field'")
					.append("name='field_").append(recordNo).append("_").append(escape(name)).append("' ")
					.append("value='").append(escape(value)).append("' ");
			if (fieldSpec != null) {
				sb.append("maxlength='").append(fieldSpec.lengthBytes).append("'");
				
			}
			sb.append("/>");

		} else {
			sb.append("<pre style='margin:0'>").append(escape(value)).append("</pre>");
		}
		sb.append("</td>");
		sb.append("</tr>\n");
		return sb.toString();
	}

	public String render(int recordNumber1Based) {
		String type = readType(recordNumber1Based).trim();
		RecordType rt = RecordType.from(type);
		List<FieldSpec> layout = SchemaRegistry.getSchema(transactionType, rt.code);
		byte[] rec = readRecordBytes(recordNumber1Based);

		StringBuilder sb = new StringBuilder(8_192);
		sb.append(
				"<div class='table-responsive'>\n<table  aria-labelledby='record-context' class='table table-sm table-striped table-bordered'>\n");
		sb.append(
				"<thead><tr><th scope='col' style='white-space:nowrap'>Field</th><th scope='col'>Value</th></tr></thead><tbody>\n");

		if (layout == null || layout.isEmpty()) {
			String recTypeValue = type;
			String byteLenValue = String.valueOf(rec.length);
			Map<String, String> recordOverlay = editOverlay != null ? editOverlay.get(recordNumber1Based) : null;
			if (recordOverlay != null) {
				recTypeValue = recordOverlay.getOrDefault("REC_TYPE", recTypeValue);
				byteLenValue = recordOverlay.getOrDefault("BYTE_LEN", byteLenValue);
			}
			sb.append(row("REC_TYPE", recTypeValue, recordNumber1Based, null));
			sb.append(row("BYTE_LEN", byteLenValue, recordNumber1Based, null));

		} else {
			for (FieldSpec f : layout) {
				int start = f.start1Based - 1;
				int len = f.lengthBytes;
				String val = null;
				switch (f.type) {
				case ALPHA:
					val = sliceTrim(rec, start, len);
					break;
				case NUMERIC_TEXT:
					int lastByte = rec[start + f.lengthBytes - 1] & 0xFF;
					int zone = (lastByte >>> 4) & 0x0F;
					if (zone != 0xF) {
						// ZONED DECIMAL
						val = decodeZonedDecimal(rec, start, f.lengthBytes);
					} else {
						val = sliceTrim(rec, start, len);

						if (val.chars().count() == 1) {
							try {
								val = String.valueOf(parseOverpunchIntSafe(val));
							} catch (NullPointerException ne) {
								val = "";
							}
						}
						if (val.contains("}")) {
							try {
								val = String.valueOf(parseOverpunchIntSafe(val));
							} catch (NullPointerException ne) {
								val = "";
							}
						}
						if (val.contains("{")) {
							try {
								val = String.valueOf(parseOverpunchIntSafe(val));
							} catch (NullPointerException ne) {
								val = "";
							}
						}
						if (val.chars().count() == 4 && !val.startsWith("X")) {// need to review this
							try {
								val = String.valueOf(parseOverpunchIntSafe(val));
							} catch (NullPointerException ne) {
								val = "";
							}
						}
					}

					break;
				case PACKED_DECIMAL:
					val = invokeFixed("decodeComp3ToString", rec, start, len, f.scale);
					if (val.contains("}")) {
						try {
							val = String.valueOf(parseOverpunchIntSafe(val));
						} catch (NullPointerException ne) {
							val = "";
						}
					}
					break;
				case BINARY:
					val = invokeFixed("decodeBinary", rec, start, len, f.scale);
					break;
				default:
					val = "";
				}

				Map<String, String> recordOverlay = editOverlay != null ? editOverlay.get(recordNumber1Based) : null;
				if (recordOverlay != null && recordOverlay.containsKey(f.name)) {
					val = recordOverlay.get(f.name);
				}
				sb.append(row(f.name, val, recordNumber1Based, f));
			}
		}
		
		
		sb.append("</tbody></table></div>\n");
		return sb.toString();
	}

	private String row(String name, String value) {
		return new StringBuilder().append("<tr><th scope='row' style='white-space:nowrap'>").append(escape(name))
				.append("</th><td><pre style='margin:0'>").append(value).append("</pre></td></tr>\n").toString();
	}

	private String sliceTrim(byte[] a, int off, int len) {
		int end = Math.min(a.length, off + len);
		int i = end - 1;
		while (i >= off && a[i] == EBCDIC_SPACE)
			i--;
		int newLen = (i < off) ? 0 : (i - off + 1);
		return new String(a, off, Math.max(0, newLen), cs);
	}

	private String invokeFixed(String method, byte[] rec, int start, int len, int scale) {
		try {
			Class<?> cls = Class.forName("com.its255.io.Fixed255Parser");
			Method m = cls.getDeclaredMethod(method, byte[].class, int.class, int.class, int.class);
			// It's private – make it accessible
			m.setAccessible(true);

			Object val = m.invoke(null, rec, start, len, scale);// Catching Exception for Binary
			return (val == null) ? "" : String.valueOf(val);
		} catch (Exception ex) {
			// ex.printStackTrace();
			return "";
		}
	}

	private byte[] readRecordBytes(int rn) {
		try {
			return (byte[]) store.getClass().getMethod("readRecordBytes", int.class).invoke(store, rn);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String readType(int rn) {
		if (transactionType.equals(FileViewerConstants.CBFBD)) {
			return FileViewerConstants.CBFBD;
		} else {
			try {
				return String.valueOf(store.getClass().getMethod("readType", int.class).invoke(store, rn));
			} catch (Exception e) {
				return "";
			}
		}
	}

	private static String escape(String s) {
		if (s == null)
			return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private boolean isEditableField(String fieldName) {
		return !List.of("REC_TYPE", "BYTE_LEN").contains(fieldName);

	}

	static int parseOverpunchInt(String s) {
		if (s == null || s.isEmpty()) {
			return (Integer) null;
		}
		char last = s.charAt(s.length() - 1);
		String body = s.substring(0, s.length() - 1);

		if (last >= '0' && last <= '9') {
			return Integer.parseInt(body + last);
		}

		Integer d;
		boolean neg = false;
		if ((d = POS.get(last)) != null) {
			neg = false;
		} else if ((d = NEG.get(last)) != null) {
			neg = true;
		} else {
//    		throw new IllegalArgumentException("Invalid overpunch char: " + last);
			System.err.println("Invalid overpunch char: " + last);
		}
		int value = Integer.parseInt(body + d);
		return neg ? -value : value;
	}

	/** Safer overpunch decoder that returns null for invalid inputs. */
	private static Integer parseOverpunchIntSafe(String s) {
		if (s == null || s.isEmpty())
			return null;
		char last = s.charAt(s.length() - 1);
		String body = s.substring(0, s.length() - 1);
		if (last >= '0' && last <= '9') {
			try {
				return Integer.parseInt(body + last);
			} catch (NumberFormatException e) {
				return null;
			}
		}
		Integer d = POS.get(last);
		boolean neg = false;
		if (d == null) {
			d = NEG.get(last);
			if (d != null)
				neg = true;
		}
		if (d == null)
			return null;
		try {
			int value = Integer.parseInt(body + d);
			return neg ? -value : value;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String decodeZonedDecimal(byte[] rec, int off, int len) {
		boolean negative = false;
		StringBuilder digits = new StringBuilder(len);

		for (int i = 0; i < len; i++) {
			int b = rec[off + i] & 0xFF;
			int hi = (b >>> 4) & 0x0F;
			int lo = b & 0x0F;

			if (i == len - 1) {
				// Sign comes from high nibble
				if (hi == 0xD)
					negative = true;
				digits.append(lo);
			} else {
				digits.append(lo);
			}
		}

		String val = digits.toString();
		// Strip leading zeros but keep "0"
		val = val.replaceFirst("^0+(?!$)", "");

		return negative ? "-" + val : val;
	}

	static final Map<Character, Integer> POS = Map.ofEntries(Map.entry('{', 0), Map.entry('A', 1), Map.entry('B', 2),
			Map.entry('C', 3), Map.entry('D', 4), Map.entry('E', 5), Map.entry('F', 6), Map.entry('G', 7),
			Map.entry('H', 8), Map.entry('I', 9), Map.entry('X', 0)// Checking on this char
	);

	static final Map<Character, Integer> NEG = Map.ofEntries(Map.entry('}', 0), Map.entry('J', 1), Map.entry('K', 2),
			Map.entry('L', 3), Map.entry('M', 4), Map.entry('N', 5), Map.entry('O', 6), Map.entry('P', 7),
			Map.entry('Q', 8), Map.entry('R', 9), Map.entry('X', 0)// Checking on this char
	);

}
