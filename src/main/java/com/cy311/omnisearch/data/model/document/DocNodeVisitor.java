package com.cy311.omnisearch.data.model.document;

public interface DocNodeVisitor<T> {
    T visitHeading(HeadingNode node);
    T visitParagraph(ParagraphNode node);
    T visitTable(TableNode node);
    T visitList(ListNode node);
    T visitImage(ImageNode node);
    T visitLink(LinkNode node);
    T visitDivider(DividerNode node);
    T visitSection(SectionNode node);
    T visitText(TextNode node);
    T visitStyledText(StyledTextNode node);
    T visitImageInline(ImageInlineNode node);
}
