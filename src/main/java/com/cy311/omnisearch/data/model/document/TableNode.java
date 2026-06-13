package com.cy311.omnisearch.data.model.document;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TableNode extends DocNode {
    private final List<String> headers;
    private final List<List<DocNode>> rows;

    public TableNode(List<String> headers, List<List<DocNode>> rows) {
        this.headers = List.copyOf(
            Objects.requireNonNull(headers, "headers must not be null"));
        this.rows = List.copyOf(
            Objects.requireNonNull(rows, "rows must not be null"));
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<List<DocNode>> getRows() {
        return rows;
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
        return headers.equals(tableNode.headers) && rows.equals(tableNode.rows);
    }

    @Override
    public int hashCode() {
        return 31 * headers.hashCode() + rows.hashCode();
    }

    @Override
    public String toString() {
        return "TableNode{headers=" + headers + ", rows=" + rows + "}";
    }
}
