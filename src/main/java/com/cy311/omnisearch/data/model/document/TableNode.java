package com.cy311.omnisearch.data.model.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TableNode extends DocNode {
    private final List<String> headers;
    private final List<List<DocNode>> rows;
    /** Parallel to {@code rows}: colspan of each cell (default 1). Null-safe: a null or
     *  shorter list means the remaining cells span 1 column. Old cached JSON (without this
     *  field) therefore deserializes to all-1 colspans. */
    private final List<List<Integer>> rowColspans;

    public TableNode(List<String> headers, List<List<DocNode>> rows) {
        this(headers, rows, null);
    }

    public TableNode(List<String> headers, List<List<DocNode>> rows, List<List<Integer>> rowColspans) {
        this.headers = List.copyOf(
            Objects.requireNonNull(headers, "headers must not be null"));
        this.rows = List.copyOf(
            Objects.requireNonNull(rows, "rows must not be null"));
        this.rowColspans = rowColspans != null ? List.copyOf(rowColspans) : null;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<List<DocNode>> getRows() {
        return rows;
    }

    /**
     * Returns the colspan of the cell at {@code rowIdx}/{@code cellIdx}, defaulting to 1
     * when no colspan metadata is present (old caches, hand-built tables).
     */
    public int getColspan(int rowIdx, int cellIdx) {
        if (rowColspans == null || rowIdx >= rowColspans.size()) {
            return 1;
        }
        List<Integer> row = rowColspans.get(rowIdx);
        if (row == null || cellIdx >= row.size()) {
            return 1;
        }
        Integer c = row.get(cellIdx);
        return c != null && c > 0 ? c : 1;
    }

    public String getType() {
        return "table";
    }

    @Override
    public <T> T accept(DocNodeVisitor<T> visitor) {
        return visitor.visitTable(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TableNode tableNode)) return false;
        return headers.equals(tableNode.headers) && rows.equals(tableNode.rows)
            && Objects.equals(rowColspans, tableNode.rowColspans);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * headers.hashCode() + rows.hashCode())
            + (rowColspans != null ? rowColspans.hashCode() : 0);
    }

    @Override
    public String toString() {
        return "TableNode{headers=" + headers + ", rows=" + rows + "}";
    }
}
