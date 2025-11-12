### OmniSearch Mod 项目记忆文档

#### 1. 项目概述

- **项目名称**: OmniSearch Mod
- **一句话简介**: 这是一个 Minecraft 模组，它在游戏内集成了一个“万能搜索”功能，允许玩家无需离开游戏，即可搜索并浏览来自中文 Minecraft 百科（mcmod.cn）等外部网站的富文本内容（包括文字、图片、链接等）。
- **核心价值**: 解决了玩家在游戏过程中需要频繁切出游戏查找资料的痛点，提供了无缝、沉浸式的游戏信息查询体验。

#### 2. 核心功能拆解

1.  **游戏内搜索界面 (GUI)**: 提供一个独立的、覆盖全屏的搜索界面。
    -   **组件**: 包含一个文本输入框用于搜索，一个内容显示区域，以及一个“返回”按钮。
    -   **交互**: 支持鼠标滚轮滚动页面，点击链接进行跳转。
2.  **外部数据获取**:
    -   **目标网站**: 主要数据源为 mcmod.cn 。
    -   **机制**: 根据用户输入的关键词，异步向目标网站的 API 发送网络请求，获取搜索结果或特定页面的 HTML 内容。
3.  **HTML 富文本渲染**:
    -   **核心挑战**: 在 Minecraft 的 GUI 环境中，从零开始渲染一个功能相对完善的 HTML 页面。
    -   **支持的元素**: 文本（支持不同颜色、样式）、图片、超链接、列表、图标等。
    -   **布局**: 实现文本的自动换行、块级元素的独立成行、图文混排等。

#### 3. 核心代码逻辑详解

整个项目的代码逻辑可以清晰地划分为三个主要层次：**视图层 (View)**、**数据层 (Data)** 和 **渲染层 (Renderer)**。

**A. 视图层 (View)**

-   **核心文件**: `c:\Users\32800\Desktop\MDK-1.21.1-ModDevGradle\src\main\java\com\cy311\omnisearch\client\gui\OmnisearchScreen.java`
-   **职责**:
    1.  构建用户能直接看到的图形界面。
    2.  接收并处理用户的输入（键盘输入、鼠标点击、滚轮滚动）。
    3.  管理页面状态和历史记录。
-   **实现逻辑**:
    1.  继承 Minecraft 的 `Screen` 类来创建一个新的 GUI 界面。
    2.  在 `init()` 方法中，初始化 `EditBox` （搜索框）和其他 UI 元素。
    3.  在 `render()` 方法中，调用 **渲染层** 的 `HtmlRenderer` 来将内容绘制到屏幕上。
    4.  通过重写 `mouseClicked()` 和 `mouseScrolled()` 方法来响应用户的鼠标操作。点击链接时，会触发 **数据层** 去获取新页面的内容；滚动滚轮则会改变 **渲染层** 的渲染起始位置。
    5.  内部维护一个 `history` 栈，每当用户点击链接跳转时，将旧页面的 URL 存入栈中，从而实现“返回”功能。

**B. 数据层 (Data)**

-   **核心文件**:
    -   `c:\Users\32800\Desktop\MDK-1.21.1-ModDevGradle\src\main\java\com\cy311\omnisearch\util\McmodFetcher.java`
    -   `c:\Users\32800\Desktop\MDK-1.21.1-ModDevGradle\src\main\java\com\cy311\omnisearch\util\ImageManager.java`
-   **职责**:
    1.  作为应用与外部世界（网络）沟通的桥梁。
    2.  根据请求，获取原始的 HTML 或 JSON 数据。
    3.  对原始数据进行预处理和清洗。
    4.  **管理图片的异步加载、缓存与渲染。**
-   **实现逻辑**:
    1.  **异步网络请求**: 使用 `CompletableFuture.supplyAsync()` 将所有网络请求都放在一个独立的后台线程中执行。这是至关重要的，因为它能防止网络延迟导致整个游戏客户端卡死。
    2.  **数据获取**: `McmodFetcher` 提供 `fetchSearchResults(term)` 和 `fetchPageContent(url)` 两个核心方法，分别用于获取搜索结果和单个页面的内容。
    3.  **HTML 清洗**: 使用 `Jsoup` 库对获取到的 HTML 进行解析和“净化”。这包括：
        -   移除不必要的元素，如广告、导航栏、评论区等。
        -   将页面中的相对 URL（如 `/item/123.html`）转换为绝对 URL（如 `https://www.mcmod.cn/item/123.html`），以确保链接和图片能被正确加载。
    4.  **图片管理**: `ImageManager` 负责处理所有图片的加载和渲染。
        -   **异步加载**: 当 `HtmlRenderer` 解析到 `<img>` 标签时，会调用 `ImageManager` 的 `loadImage` 方法。该方法会返回一个占位符，并立即启动一个后台线程去下载图片。
        -   **缓存**: 下载的图片会被缓存在内存中，避免重复下载。
        -   **渲染回调**: 图片下载完成后，`ImageManager` 会触发一次界面重绘，将占位符替换为真实的图片。

**C. 渲染层 (Renderer)**

-   **核心文件**: `c:\Users\32800\Desktop\MDK-1.21.1-ModDevGradle\src\main\java\com\cy311\omnisearch\client\gui\HtmlRenderer.java`
-   **职责**: 这是整个项目的技术核心和灵魂所在。它负责将一串标准的 HTML 文本，转换为能在 Minecraft 界面上显示的、带有丰富格式的像素图形。
-   **实现逻辑**:
    1.  **数据模型**:
        -   `RenderablePart`: 定义了渲染的基本“单元”，它是一个抽象基类，有三个具体的子类：
            -   `StyledPart`: 代表一段带样式的文本。
            -   `ImagePart`: 代表一张图片。
            -   `IconPart`: 代表一个小图标（如血量、攻击力图标）。
        -   `RenderableLine`: 代表一个渲染“行”，它由一个或多个 `RenderablePart` 组成。
    2.  **解析与布局 (`prepare` & `processNode`)**:
        -   `prepare(html)` 方法是渲染的入口。它接收 HTML 字符串，并使用 `Jsoup` 将其解析成一个 DOM 树。
        -   `processNode(node)` 是一个递归方法，它会深度优先遍历整个 DOM 树：
            -   **文本节点**: 这是最复杂的部分。它会获取文本，按空格分割成单词。然后逐个单词地判断是否会超出当前行的宽度限制。如果超出，则调用 `startNewLine()` 换行。如果一个单词本身就超长，则会退化为按字符进行换行。这是实现 **自动换行** 的核心算法。
            -   **元素节点** (如 `<a>`, `<img>`, `<p>`):
                -   **块级元素** (`<p>`, `<div>`, `<h1>` 等): 在处理该元素前后，会强制调用 `startNewLine(true)`，以确保它在布局上独占一行。
                -   **内联元素** (`<a>`, `<span>`): 会修改当前的样式（如颜色、链接 URL），但不会主动换行。
                -   **图片元素** (`<img>`): 会创建一个 `ImagePart`，并使用 **数据层** 的 `ImageManager` 去异步加载图片。
    3.  **样式管理 (`Style` & `styleStack`)**:
        -   为了模拟 CSS 的样式继承规则，代码使用了一个 `Style` 对象的栈 (`styleStack`)。当递归进入一个带样式的 HTML 标签时（如 `<span style="color:red">`），会创建一个新的 `Style` 对象并压入栈顶；当处理完该标签的所有子节点后，再从栈顶弹出，从而恢复到父节点的样式。
    4.  **最终渲染 (`render`)**:
        -   这个方法负责最后的绘制工作。它会遍历 `prepare` 方法生成的所有 `RenderableLine` 列表。
        -   对于每一行，它再遍历其中的每一个 `RenderablePart`，调用 Minecraft 底层的 `font.draw()` 或 `GuiComponent.blit()` 等方法，将文本和图片真切地绘制到屏幕上。

#### 4. 构建与环境配置

-   **核心问题**: 项目在构建过程中，处理包含中文字符的 `gradle.properties` 文件时，出现了编码错误，导致游戏内模组信息乱码。
-   **解决方案**:
    1.  在 `gradle.properties` 文件中添加 `org.gradle.jvmargs=-Dfile.encoding=UTF-8`，以确保 Gradle 守护进程在启动时使用 UTF-8 编码。
    2.  修改 `build.gradle` 文件，在 `generateModMetadata` 任务中，强制以 `UTF-8` 编码读取 `gradle.properties` 文件。这从根本上解决了构建时读取属性文件的编码问题。

#### 总结

这个 Mod 的实现非常精巧。它本质上是在 Minecraft 游戏内，用 Java 从零实现了一个微型的、高度定制化的 HTML 渲染引擎。它虽然不支持完整的 HTML/CSS 规范，但精准地实现了在游戏攻略和资料展示场景下最需要的核心功能（文本样式、图片、链接、自动换行）。代码结构清晰，分层合理，并且通过异步网络请求和图片加载，保证了流畅的用户体验，是 Minecraft Mod 开发领域中一个关于复杂 GUI 和网络编程的绝佳学习范例。