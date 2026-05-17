    
    function setClearCacheEnabled(enabled) {
      const btn = document.getElementById('btnClear');
      if (!btn) return;
    
      btn.disabled = !enabled;
    
      if (!enabled) {
        btn.setAttribute('aria-disabled', 'true');
      } else {
        btn.removeAttribute('aria-disabled');
      }
    }
    
    function setUploadEnabled(enabled) {
      const fileInput = document.getElementById('fileInput');
      const txnSelect = document.getElementById('txnType');
      const uploadBtn = document.getElementById('btnUpload');

      if (fileInput) {
        fileInput.disabled = !enabled;
        if (!enabled) fileInput.value = ''; // safety
      }

      if (txnSelect) {
        txnSelect.disabled = !enabled;
        if (!enabled) {
          txnSelect.setAttribute('aria-disabled', 'true');
        } else {
          txnSelect.removeAttribute('aria-disabled');
        }
      }
      if (uploadBtn) {
        uploadBtn.disabled = !enabled;
        if (!enabled) {
          uploadBtn.setAttribute('aria-disabled', 'true');
        } else {
          uploadBtn.removeAttribute('aria-disabled');
        }
      }
    }
    
    function updateUploadHint(hasFile) {
      const hint = document.getElementById('fileHint');
      if (!hint) return;
    
      if (hasFile) {
        hint.textContent =
          'A file is already cached for this session. Clear the cached file to upload a new one.';
        hint.classList.add('text-warning');
        disableFileUpload(true);
      } else {
        hint.textContent = 'Choose a file to upload.';
        hint.classList.remove('text-warning');
        disableFileUpload(false);
      }
    }
    function disableFileUpload(hasFile){
const fileInput=document.getElementById('fileInput');
if(!fileInput)return;
fileInput.disabled=hasFile;
if(hasFile){
fileInput.classList.add('disabled');
}else {
fileInput.classList.remove('disabled');
}
}
    
    // ---- Server-side progress poller (existing logic) ----
    async function poll() {
      try {
        const r = await fetch('/viewer/progress?ts=' + Date.now(), { cache: 'no-store' });
        const j = await r.json();
        const serverPct = Math.round((j.progress || 0) * 100);
        // Upload phase maps to 0-80; server parse maps to 80-100
        const mapped = Math.min(100, Math.max(80, 80 + Math.round(serverPct * 0.20)));
        const el = document.getElementById('pbar');
        if (el) {
          el.style.width = mapped + '%';
          el.innerText = mapped + '%';
          el.setAttribute('aria-valuenow', String(mapped));
        }
        if (mapped < 100) setTimeout(poll, 800);
      } catch (e) {
        setTimeout(poll, 1200);
      }
    }

    // ---- Utilities for enabling/disabling Search & Export ----
    function setActionsEnabled(enabled) {
      const btnSearch = document.getElementById('btnSearch');
      const btnExport = document.getElementById('btnExport');
      if (btnSearch) btnSearch.disabled = !enabled;
      if (btnExport) {
        if (enabled) {
          btnExport.classList.remove('disabled');
          btnExport.removeAttribute('aria-disabled');
        } else {
          btnExport.classList.add('disabled');
          btnExport.setAttribute('aria-disabled', 'true');
        }
      }
    }

    // ---- Filter Record Type by Transaction Type ----
    function initTxnRecordTypeFilter() {
      const txnSelect   = document.getElementById('txnType');
      const recordType  = document.getElementById('recordTypeCode');
      if (!txnSelect || !recordType) return;

      const rtHelpId = "recordTypeHelp";
      if (!document.getElementById(rtHelpId)) {
        const help = document.createElement("div");
        help.className = "form-text";
        help.id = rtHelpId;
        help.textContent = "This list is filtered by Transaction Type. Choose a transaction type first.";
        recordType.insertAdjacentElement("afterend", help);
      }
      recordType.setAttribute("aria-describedby", rtHelpId);

      // Harvest existing options (from backend)
      const allOptions = [];
      Array.from(recordType.querySelectorAll("option")).forEach(opt => {
        const val = (opt.value || "").trim();
        const label = (opt.textContent || "").trim();
        if (val === "" || !label) return; // ignore placeholder
        allOptions.push({ code: val, label });
      });

      // Mapping (per your spec)
      const TXN_MAP = {
        "SF-Institutional": new Set([
          "05","10","15","20","30","31","32","33",
          "40","41","42","43","44","45","46","47",
          "50","60","65","66","71","72","73","74",
          "80","90","9D"
        ]),
        "SF-Professional": new Set([
          "A5","B0","B5","C0",
          "D0","D1","D2","D3",
          "E0","E1","E2","E6",
          "F0","F1","F5","F6",
          "G0","X0"
        ]),
        "DF": new Set([
          "1A","2A","2B","2C","2E","2F","2G","2H",
          "3A","3C","3D",
          "4A","4B","4C","4D",
          "8A","9A"
        ]),
      "CBF": new Set([
        "5A","6A","6B","7A","7B","9A"
      ]),
      "RF": new Set([
        "RC_31","RC_32"
      ]),
      
    "NF": new Set([
      "NF_81",
      "NF_82",
      "NF_83",
      "NF_84"
    ]), 
    "MemberExchange": new Set([
      "ME_40D",
      "ME_41A",
      "ME_41B",
      "ME_41C",
      "ME_42A",
      "ME_42C",
      "ME_43A",
      "ME_43B",
      "ME_43C",
      "ME_49A"
    ])
  };


  const RECORD_TYPES_BY_TRANSACTION = {
    "SFI": [
      ["05","05 — FM105"],["10","10 — FM110"],["15","15 — FM115"],["20","20 — FM120"],
      ["30","30 — FM130"],["31","31 — FM131"],["32","32 — FM132"],["33","33 — FM133"],
      ["40","40 — FM140"],["41","41 — FM141"],["42","42 — FM142"],["43","43 — FM143"],
      ["44","44 — FM144"],["45","45 — FM145"],["46","46 — FM146"],["47","47 — FM147"],
      ["50","50 — FM150"],["60","60 — FM160"],["65","65 — FM165"],["66","66 — FM166"],
      ["71","71 — FM171"],["72","72 — FM172"],["73","73 — FM173"],["74","74 — FM174"],
      ["80","80 — FM180"],["90","90 — FM190"],["9D","9D — FM9D"]
    ],

    "SFP": [
      ["A5","A5 — FM1A5"],["B0","B0 — FM1B0"],["B5","B5 — FM1B5"],["C0","C0 — FM1C0"],
      ["D0","D0 — FM1D0"],["D1","D1 — FM1D1"],["D2","D2 — FM1D2"],["D3","D3 — FM1D3"],
      ["E0","E0 — FM1E0"],["E1","E1 — FM1E1"],["E2","E2 — FM1E2"],["E6","E6 — FM1E6"],
      ["F0","F0 — FM1F0"],["F1","F1 — FM1F1"],["F5","F5 — FM1F5"],["F6","F6 — FM1F6"],
      ["G0","G0 — FM1G0"],["X0","X0 — FM1X0"]
    ],

    "DF": [
      ["1A","1A — FM21A"],["2A","2A — FM22A"],["2B","2B — FM22B"],["2C","2C — FM22C"],
      ["2E","2E — FM22E"],["2F","2F — FM22F"],["2G","2G — FM22G"],["2H","2H — FM22H"],
      ["3A","3A — FM23A"],["3C","3C — FM23C"],["3D","3D — FM23D"],
      ["4A","4A — FM24A"],["4B","4B — FM24B"],["4C","4C — FM24C"],["4D","4D — FM24D"],
      ["8A","8A — FM28A"],["9A","9A — FM29A"]
    ],

    "CBF": [
      ["5A","5A — FM35A"],["6A","6A — FM36A"],["6B","6B — FM36B"],
      ["7A","7A — FM37A"],["7B","7B — FM37B"],["9A","9A — FM39A"]
    ],

    "RF": [
      ["1A","1A — FM31A"],
      ["2A","2A — FM32A"]
    ],

    "CBFBD": [
      ["CBFBD","CBFBD— ITCBFBD"]
    ],

    "NF": [
      ["81","81 — FM81A"],
      ["82","82 — FM82A"],
      ["83","83 — FM83A"],
      ["84","84 — FM84A"]
    ],

    "MEF": [
      ["40D","40D — FM40D"],["41A","41A — FM41A"],["41B","41B — FM41B"],
      ["41C","41C — FM41C"],["42A","42A — FM42A"],["42C","42C — FM42C"],
      ["43A","43A — FM43A"],["43B","43B — FM43B"],
      ["43C","43C — FM43C"],["49A","49A — FM49A"]
    ],
    "PPU": [
      ["51", "51 — FM51A"],
      ["52", "52 — FM52A"],
      ["53", "53 — FM53A"],
      ["54", "54 — FM54A"],
      ["55", "55 — FM55A"],
      ["56", "56 — FM56A"],
      ["57", "57 — FM57A"],
      ["59", "59 — FM59A"]
    ],
    "PPA": [      
      ["61", "61 — FM61A"],
      ["62", "62 — FM62A"]
    ]
  };


 function setRecordTypeDisabled(isDisabled) {
        recordType.disabled = !!isDisabled;
        if (isDisabled) recordType.setAttribute("aria-disabled", "true");
        else recordType.removeAttribute("aria-disabled");
      } 
	  function resetRecordType() {
        recordType.innerHTML = '<option value="">Choose…</option>';
        setRecordTypeDisabled(true);
      }


      function populateRecordTypesFor(txn) {
        recordType.innerHTML = '<option value="">Choose…</option>';

        if (!txn) {
          setRecordTypeDisabled(true);
          return;
        }

        const list = RECORD_TYPES_BY_TRANSACTION[txn] || [];

        for (const pair of list) {
          const el = document.createElement("option");
          el.value = pair[0];
          el.textContent = pair[1];
          recordType.appendChild(el);
        }

        setRecordTypeDisabled(list.length === 0);
      }

      // Initial + events
      resetRecordType();
      txnSelect.addEventListener("change", () => {
        recordType.value = "";
        populateRecordTypesFor(txnSelect.value);
      });
      window.addEventListener("pageshow", () => {
        populateRecordTypesFor(txnSelect.value);
      });

      // expose for session restore
      return { resetRecordType, populateRecordTypesFor, txnSelect, recordType };
    }

    // ---- Persistence helpers ----
    const KEY = (name) => `viewer.${name}`;
    function clearSearchStorage() {
      try {
        sessionStorage.removeItem(KEY('sccf'));
        sessionStorage.removeItem(KEY('recNo'));
        sessionStorage.removeItem(KEY('txnType'));
        sessionStorage.removeItem(KEY('recordTypeCode'));
      } catch (_) {}
    }
    
    //
    /*if record type is not select show vertical view hide Horizontal view*/
function toggleViewBasedOnRecordType(){
	const recordType=document.getElementById("recordTypeCode").value;
	const verticalView=document.getElementById("verticalView");
	const horizontalView=document.getElementById("horizontalView");
	if(recordType && recordType.trim()!==""){
		verticalView.style.display="none";
		horizontalView.style.display="block";
	} else {
		verticalView.style.display="block";
		horizontalView.style.display="none";
		
	}
}
    
    //

    // ---- SessionStorage: persist & restore search fields; restart-aware; Clear buttons ----
    (function initSessionStoragePersistence() {
      document.addEventListener('DOMContentLoaded', () => {
        const searchForm = document.getElementById('searchForm');
        const sccfInput  = document.getElementById('sccf');
        const txnSelect  = document.getElementById('txnType');
        const rtSelect   = document.getElementById('recordTypeCode');
        const recNoInput = document.getElementById('recNo');

        // Initialize Txn/RecordType filter and get its helpers
        window.filterAPI = initTxnRecordTypeFilter();
        const filterAPI = window.filterAPI;
        // Restart-aware clearing using data-has-file
        const body = document.body;
        const hasFileNow   = body.getAttribute('data-has-file') === 'true';
        const lastHasFile  = sessionStorage.getItem(KEY('lastHasFile')) === 'true';

        
        if (lastHasFile && !hasFileNow) {
          clearSearchStorage();

          if (sccfInput) sccfInput.value = '';
          if (recNoInput) recNoInput.value = '';
          if (filterAPI && !hasFileNow) filterAPI.resetRecordType();
          if (rtSelect) rtSelect.value = '';

          if (txnSelect) {
            txnSelect.value = '';
            txnSelect.disabled = false;
            txnSelect.removeAttribute('aria-disabled');
          }
        }
        try { sessionStorage.setItem(KEY('lastHasFile'), hasFileNow ? 'true' : 'false'); } catch (_) {}

        // RESTORE from sessionStorage
        try {
          const storedTxn  = sessionStorage.getItem(KEY('txnType')) || '';
          const storedRT   = sessionStorage.getItem(KEY('recordTypeCode')) || '';
          const storedSccf = sessionStorage.getItem(KEY('sccf')) || '';
          const storedRec  = sessionStorage.getItem(KEY('recNo')) || '';

          if (txnSelect && storedTxn) { txnSelect.value = storedTxn; }
          if (filterAPI && txnSelect) { filterAPI.populateRecordTypesFor(txnSelect.value); }

          // Try to set RT immediately…
          if (rtSelect && storedRT) {
            rtSelect.value = storedRT;
          }

          // …and reapply shortly after population if it didn't take yet  // PERSIST FIX
          if (rtSelect && storedRT && rtSelect.value !== storedRT) {
            requestAnimationFrame(() => {
              rtSelect.value = storedRT;
            });
          }

          setUploadEnabled(!hasFileNow);
          updateUploadHint(hasFileNow);
          setClearCacheEnabled(hasFileNow);

          if (hasFileNow && txnSelect && txnSelect.value) {
            txnSelect.disabled = true;
            txnSelect.setAttribute('aria-disabled', 'true');
           
            if (rtSelect) {
              rtSelect.disabled = false;
            }
            setActionsEnabled(true);
          }

          if (sccfInput && storedSccf){ sccfInput.value = storedSccf; }
          if (recNoInput && storedRec){ recNoInput.value = storedRec; }
        } catch (e) {}

        // Also reapply RT on pageshow in case of bfcache timing  // PERSIST FIX
        window.addEventListener('pageshow', () => {
          const storedRT = sessionStorage.getItem(KEY('recordTypeCode')) || '';
          if (rtSelect && storedRT && rtSelect.value !== storedRT) {
            rtSelect.value = storedRT;
          }
        });

        // PERSIST on change/submit
        function persist() {
          try {
            if (txnSelect)  sessionStorage.setItem(KEY('txnType'), txnSelect.value || '');
            if (rtSelect)   sessionStorage.setItem(KEY('recordTypeCode'), rtSelect.value || '');
            if (sccfInput)  sessionStorage.setItem(KEY('sccf'), sccfInput.value || '');
            if (recNoInput) sessionStorage.setItem(KEY('recNo'), recNoInput.value || '');
          } catch (e) {}
        }
        if (txnSelect)  txnSelect.addEventListener('change', () => {
          if (filterAPI) filterAPI.populateRecordTypesFor(txnSelect.value);
          
          const txnHint = document.getElementById('txnTypeHelp');
          if (txnHint) {
            txnHint.classList.remove('txn-hint-error');
            txnHint.removeAttribute('role');   // stop alerting
          }

          if (rtSelect)  rtSelect.value = '';
          persist();
        });
        if (rtSelect)   rtSelect.addEventListener('change', persist);
        if (sccfInput)  sccfInput.addEventListener('input', persist);
        if (recNoInput) recNoInput.addEventListener('input', persist);
        if (searchForm) searchForm.addEventListener('submit', () => { persist(); setScrollToResultsFlag(); });

        // Clear buttons
        const btnClear = document.getElementById('btnClear');      // Clear cached file (server)
        const clearForm = document.getElementById('clearForm');
        if (btnClear && clearForm) {
          btnClear.addEventListener('click', () => {
            if (sccfInput)  sccfInput.value = '';
            if (recNoInput) recNoInput.value = '';
            if (txnSelect)  txnSelect.value = '';
            if (filterAPI)  filterAPI.resetRecordType();
            if (rtSelect)   rtSelect.value = '';
            clearSearchStorage();
            setUploadEnabled(true); // UNLOCK upload
            updateUploadHint(false); // restore message
            try { sessionStorage.setItem(KEY('lastHasFile'), 'false'); } catch (_) {}
            setScrollToResultsFlag(); // consistent scroll target on reload
          });
        }

        const btnClearSearch = document.getElementById('btnClearSearch'); // Clear Search (client only)
        if (btnClearSearch) {
          btnClearSearch.addEventListener('click', () => {
            if (sccfInput)  sccfInput.value = '';
            if (recNoInput) recNoInput.value = '';
            if (filterAPI)  filterAPI.resetRecordType();            
            if (txnSelect && txnSelect.value) {
              filterAPI.populateRecordTypesFor(txnSelect.value);
            }
            if (rtSelect)   rtSelect.value = '';
            clearSearchStorage();
            if (sccfInput) sccfInput.focus();
          });
        }
      });
    })();    

    // ---------- Scroll to results (table start) ----------
    function setScrollToResultsFlag() {
      try { sessionStorage.setItem(KEY('scrollToResults'), 'true'); } catch (_) {}
    }
    function maybeScrollToResults() {
      try {
        const flag = sessionStorage.getItem(KEY('scrollToResults')) === 'true';
        if (flag) {
          const anchor = document.getElementById('resultsTop') || document.querySelector('.record-container');
          if (anchor) anchor.scrollIntoView({ behavior: 'auto', block: 'start' });
        }
        sessionStorage.removeItem(KEY('scrollToResults'));
      } catch (_) {}
    }

    // ---- Main DOMContentLoaded wiring for upload & buttons ----
    document.addEventListener('DOMContentLoaded', () => {
      const uploadForm = document.querySelector('form[action="/viewer/upload"]');
      const fileInput  = uploadForm?.querySelector('input[type="file"][name="file"]');
      const pbar       = document.getElementById('pbar');

      // Initialize action buttons from server-rendered state (hasFile):
      const searchInitiallyDisabled = document.getElementById('btnSearch')?.hasAttribute('disabled');
      const exportInitiallyDisabled = document.getElementById('btnExport')?.classList.contains('disabled');
      setActionsEnabled(!(searchInitiallyDisabled || exportInitiallyDisabled));

      if (fileInput && pbar) {
        fileInput.addEventListener('change', () => {
          pbar.classList.remove('bg-danger');
          pbar.style.width = '0%';
          pbar.innerText = '0%';
        });
      }

      if (uploadForm && pbar) {
        uploadForm.addEventListener('submit', (ev) => {
          ev.preventDefault();
          const txnSelect = document.getElementById('txnType');
          const txnHint   = document.getElementById('txnTypeHelp');
          console.log(txnSelect.value)
          if (!txnSelect || !txnSelect.value) {
            if (txnHint) {
              txnHint.classList.add('txn-hint-error');
              txnHint.setAttribute('role', 'alert');
            }
            txnSelect && txnSelect.focus();
            return; // stop upload
          }

          const file = fileInput?.files?.[0];

          // Lock actions while uploading
          setActionsEnabled(false);

          if (!file) {
            // No new file selected; submit normally (server may use cached file)
            return uploadForm.submit();
          }

          const formData = new FormData(uploadForm);
          const xhr = new XMLHttpRequest();
          xhr.open('POST', '/viewer/upload', true);

          xhr.upload.onprogress = (e) => {
            if (!e.lengthComputable) return;
            const rawPct = (e.loaded / e.total) * 100;
            const mapped = Math.max(0, Math.min(80, Math.round(rawPct * 0.80)));
            pbar.style.width = mapped + '%';
            pbar.innerText = mapped + '%';
          };

          xhr.onload = () => {
            if (xhr.status >= 200 && xhr.status < 300) {
              // Upload OK → enable actions and start server-side poll
              setActionsEnabled(true);
              setUploadEnabled(false); // LOCK upload after success
              updateUploadHint(true);   // switch message
              setClearCacheEnabled(true);
              const txnSelect = document.getElementById('txnType');
              const recordType = document.getElementById('recordTypeCode');

              if (txnSelect) {
                txnSelect.disabled = true;
                txnSelect.setAttribute('aria-disabled', 'true');
              }

              if (recordType && txnSelect) {
                filterAPI.populateRecordTypesFor(txnSelect.value);
                recordType.disabled = false;
               
              }
              setTimeout(poll, 300);
            } else {
              // Error → keep disabled, mark bar red
              pbar.classList.add('bg-danger');
              pbar.style.width = '100%';
              pbar.innerText = 'Upload error';
              setActionsEnabled(false);
            }
          };

          xhr.onerror = () => {
            pbar.classList.add('bg-danger');
            pbar.style.width = '100%';
            pbar.innerText = 'Network error';
            setActionsEnabled(false);
          };

          pbar.classList.remove('bg-danger');
          pbar.style.width = '1%';
          pbar.innerText = '1%';
          xhr.send(formData);
        });
      }

      // set scroll flag when using Prev/Next forms (top & bottom)
      document.querySelectorAll('form[data-nav="true"]').forEach((f) => {
        f.addEventListener('submit', () => { setScrollToResultsFlag(); });
      });
    });

    // Scroll after the page is shown (normal loads + bfcache)
    window.addEventListener('pageshow', maybeScrollToResults);

    
    