package com.its255.viewer;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.its255.util.LoggingUtil;

/**
 * One-per-HTTP-session state bucket for Fast Viewer.
 * This replaces controller fields (which were singletons).
 */
public class ViewerSession implements AutoCloseable {

    public Path filePath;                 // path to uploaded file
    public String originalFilename;       // file name for UI
    public Object store;                  // ChunkedMMapRecordStore
    public PrefixIndex pidx;              // SCCF prefix index
    public FastRecordFilter filter;       // filter using pidx
    public RecordNavigator nav;           // navigation state
    public List<Integer> lastFiltered;    // last result set
    public double progress;               // prefix index progress (0..1)
    public String transactionType;
    public Path sessionDir; // per-session temp directory
    public  boolean editMode=false;
    public String selectedRecordType;
    
    

    public boolean hasFile() {
        return filePath != null && store != null;
    }
    public Map<Integer,Map<String,String>>editOverlay=new HashMap<>();
    @Override
    public void close() {
        if (store instanceof AutoCloseable ac) {
            try {
                ac.close();
            } catch (Exception ignore) {
            	LoggingUtil.error(ignore);
            } finally {
                store = null;
            }
        }
    }
}