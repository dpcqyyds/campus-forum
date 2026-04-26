import MarkdownIt from 'markdown-it'

function escapeHtml(text = '') {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: false,
  langPrefix: 'language-'
})

const defaultLinkOpen = markdown.renderer.rules.link_open || ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))
markdown.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noreferrer')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

const defaultImage = markdown.renderer.rules.image || ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))
markdown.renderer.rules.image = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('loading', 'lazy')
  return defaultImage(tokens, idx, options, env, self)
}

export function renderMarkdownToHtml(markdownText = '') {
  return markdown.render(String(markdownText || ''))
}

export function renderFormattedContent(content = '') {
  return renderMarkdownToHtml(content)
}

export function renderPlainTextToHtml(content = '') {
  return escapeHtml(content).replace(/\n/g, '<br/>')
}

export function normalizeExternalLink(value = '') {
  const text = String(value).trim()
  if (!text) return ''
  if (/^https?:\/\//i.test(text)) return text
  return `https://${text}`
}
