## 大文件崩溃分析报告

1. **导入流程问题**：
虽然实现了按行流式读取(`BufferedReader`)，但存在极端情况导致的致命 OOM：`hasChapterTitles` 函数只会扫描前两万行以匹配正则表达式。如果匹配成功，则走按章节分割的逻辑。但如果整本书后续内容没有匹配的章节名，或者该书就是一个超长单章节（无章节标题），`StringBuilder` (currentContent) 会无限 append 整个文件的字符串（几十甚至上百 MB），最后在转换为 String 和封装为 Chapter 对象时直接导致 Java Heap OOM。

2. **内存长时间驻留问题**：
`ReaderScreen` 中通过懒加载(`getChaptersMetadataList`) 确实避免了一次性持有全部正文，但在用户使用**全文搜索**时，`MainViewModel.searchFullText` 调用了 `getChaptersListForBook`。该 SQL 查询会把对应 bookId 下的所有 `Chapter` 的所有正文内容一次性全部从 Room 取出放进 `List<Chapter>` 中。如果书籍有几十 MB，则直接导致瞬间 OOM 或内存暴涨并触发 GC 导致 ANR。

3. **Room 的问题**：
导入大文件时虽然设计了 `maxBatchSize = 50` 批量插入，但是如果没匹配上章节标题，导致只有 1 个 50MB 的超大 Chapter 时，Room 单次写入极大数据（超过 Cursor 限制，通常为 2MB-4MB），会抛出 `CursorWindowAllocationException` 或直接卡死主线程/IO线程导致 ANR。

4. **崩溃具体归类**：
- **Java Heap OOM**: `currentContent` 累积超长 String 以及全书搜索拉取全部正文时。
- **主线程阻塞 ANR**: 虽然导入放在 `Dispatchers.IO` 中，但 OOM 引起的 GC 风暴会冻结所有线程。而且单次处理几百万字符的 String 操作（如正则表达式、substring 等）耗时极长。
- **数据库操作异常**: 超过 CursorWindow 上限。

---
## 第二阶段方案

1. **重构导入逻辑**：引入更智能的 Chunk 机制。不管是否有章节标题，我们都要确保单个 `Chapter` 对象内部的 `content` 不会超过安全大小（比如 5 万字上限）。如果匹配不到章节或者章节过长，自动进行分页（Chunk），确保流式读取永远不会在内存中累积超过安全上限的 String。
2. **重构全书搜索逻辑**：废弃把所有章节拉到内存搜索的做法，改在数据库层面使用 SQL 搜索（LIKE，或者按需分段读取）。
3. **保持 UI 不变**：底层的 `Chapter` 和 `Book` 逻辑调整对 UI 尽量透明。
