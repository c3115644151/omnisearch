package com.cy311.omnisearch.data;

/**
 * 一个不可变的数据记录，用于存储从 MC 百科网站上抓取到的物品信息。
 * 使用 Java Record 特性可以自动获得构造函数、getter、equals、hashCode 和 toString 方法。
 *
 * @param title 物品的官方标题。
 * @param modName 物品所属的模组名称。
 * @param url 指向该物品在 MC 百科上的完整 URL。
 * @param descriptionLines 物品的详细介绍，按段落或列表项分割成多行。
 */
public record ItemData(
    String title,
    String modName,
    String url,
    String htmlContent
) {
    // Record 的特性使其成为一个理想的、轻量级的数据载体。
}
