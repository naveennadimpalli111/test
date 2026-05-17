
package com.its255.viewer;

import java.util.*;
import java.util.stream.Collectors;


/**
 * Patch: trims record type comparisons so inputs like "1" match underlying "1 " values.
 */
public class FastRecordFilter {
  private final Object store; // reflection: readSccf, readType, getRecordCount
  private final PrefixIndex pidx;
  public FastRecordFilter(Object store, PrefixIndex pidx) { this.store = store; this.pidx = pidx; }
  private String readSccf(int rn) {
    try { return (String) store.getClass().getMethod("readSccf", int.class).invoke(store, rn); }
    catch (Exception e) { throw new RuntimeException(e); }
  }
  private String readType(int rn) {
    try { return ((String) store.getClass().getMethod("readType", int.class).invoke(store, rn)); }
    catch (Exception e) { throw new RuntimeException(e); }
  }
  private long getRecordCount() {
    try { return (long) store.getClass().getMethod("getRecordCount").invoke(store); }
    catch (Exception e) { throw new RuntimeException(e); }
  }
  public List<Integer> apply(RecordQuery q) {
    List<Integer> base = null; // 1-based record numbers
    if (q.sccf().isPresent() && !q.sccf().get().isBlank()) {
      String s = q.sccf().get().trim();
      int len = s.length();
      int maxIdx = pidx.getMaxLen();
      if (len < 15) {
        int use = Math.min(maxIdx, Math.max(1, len));
        base = pidx.get(use, s.substring(0,use)).map(arr -> {
          List<Integer> out = new ArrayList<>(arr.length);
          for (int v : arr) out.add(v);
          return out; }).orElseGet(ArrayList::new);
        if (len > use) {
          String longer = s;
          base = base.stream().filter(r -> readSccf(r).startsWith(longer)).collect(Collectors.toList());
        }
      } else if (len == 15) {
        int use = Math.min(maxIdx, 10);
        List<Integer> cand = pidx.get(use, s.substring(0,use)).map(arr -> {
          List<Integer> out = new ArrayList<>(arr.length);
          for (int v : arr) out.add(v);
          return out; }).orElseGet(ArrayList::new);
        base = cand.stream().filter(r -> readSccf(r).equals(s)).collect(Collectors.toList());
      } else base = List.of();
    }
    if (q.recordType().isPresent() && !q.recordType().get().isBlank()) {
      String t = q.recordType().get().trim();
      if (base == null) {
        base = new ArrayList<>();
        for (int r=1; r<=getRecordCount(); r++) if (readType(r).trim().equals(t)) base.add(r);
      } else {
        base = base.stream().filter(r -> readType(r).trim().equals(t)).collect(Collectors.toList());
      }
    }
    if (q.recordNumber().isPresent()) {
      int rn = q.recordNumber().get();
      if (base == null) base = List.of(rn); else base = base.stream().filter(r -> r == rn).collect(Collectors.toList());
    }
    if (base == null) { base = new ArrayList<>(); for (int r=1; r<=getRecordCount(); r++) base.add(r); }
    return base.stream().distinct().collect(Collectors.toList());
  }
}
