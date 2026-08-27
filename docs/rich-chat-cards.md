# Rich chat card protocol

Customer Chat and the Agent debugger render ordinary responses as Markdown. A response can also
contain one or more fenced `ok-card` JSON blocks. The complete block remains part of the persisted
assistant message, so conversation history can reconstruct the UI without a separate card store.

When an action has a `value`, clicking it sends that value as the next user message through the
existing chat endpoint. When an action only has an `url`, it opens an HTTP(S) link in a new tab.
Unknown fields are ignored, invalid cards fall back to visible Markdown, and at most four actions
and four product features are rendered.

## Choice card

Add the following rule to an Agent system prompt when it should ask questions that have a small,
known answer set:

````markdown
不要要求用户手动输入“是/否”或从少量固定选项中输入答案。此时在简短引导语后输出一个
`ok-card` JSON 代码块。按钮的 `value` 是点击后作为下一条用户消息发送给你的完整语义，
不能只使用含义不明的序号。

```ok-card
{
  "type": "choice",
  "title": "要继续查看适合你的方案吗？",
  "description": "只需要选择一项，我会据此继续。",
  "actions": [
    { "label": "是，继续推荐", "value": "是，请继续为我推荐方案", "style": "primary" },
    { "label": "暂时不用", "value": "否，暂时不需要推荐", "style": "secondary" }
  ]
}
```
````

## Product card

Use one card per recommended product. `image`, action `url`, and all display fields are optional;
URLs must use HTTP(S). Prices are display strings so the Agent can preserve currency and units.

````markdown
```ok-card
{
  "type": "product",
  "eyebrow": "通勤首选",
  "badge": "匹配度 96%",
  "title": "轻量降噪耳机 Pro",
  "description": "适合每天通勤、重视佩戴舒适度的用户。",
  "image": "https://example.com/product.jpg",
  "features": ["主动降噪", "32 小时续航", "轻至 238g"],
  "price": "¥1,299 起",
  "originalPrice": "¥1,499",
  "actions": [
    { "label": "就选这款", "value": "我选择轻量降噪耳机 Pro，请继续", "style": "primary" },
    { "label": "查看详情", "url": "https://example.com/products/headphone" }
  ]
}
```
````

Do not wrap multiple cards in a JSON array. Emit repeated `ok-card` blocks so explanatory Markdown
can be placed before, between, or after them.
