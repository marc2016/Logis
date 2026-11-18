import type { Page } from '@playwright/test'

/**
 * Layout problems of the visible controls (buttons, links, inputs, selects, comboboxes) inside `root`:
 * pairs that overlap by more than 3 px (nested controls excepted) and controls that stick out of the
 * window or of their dialog. Controls inside a frame that scrolls sideways (wide tables) may extend
 * past the window; they are clipped and reached by scrolling.
 */
export async function layoutProblems(page: Page, root = 'body'): Promise<string[]> {
  return page.evaluate((rootSelector) => {
    const scope = document.querySelector(rootSelector)
    if (!scope) return [`${rootSelector} not found`]
    const name = (e: Element) => {
      const label = e.getAttribute('aria-label') || e.id || (e as HTMLElement).innerText || e.getAttribute('placeholder') || ''
      return `${e.tagName.toLowerCase()} "${label.trim().replace(/\s+/g, ' ').slice(0, 40)}"`
    }
    const inScrollFrame = (e: Element) => {
      for (let p = e.parentElement; p && p !== scope; p = p.parentElement) {
        const overflow = getComputedStyle(p).overflowX
        if ((overflow === 'auto' || overflow === 'scroll') && p.scrollWidth > p.clientWidth) return true
      }
      return false
    }
    const controls = [...scope.querySelectorAll('button, a[href], input, select, textarea, [role=combobox]')].filter((e) => {
      const r = e.getBoundingClientRect()
      const style = getComputedStyle(e)
      // Screen-reader-only elements are 1 px wide; hidden native inputs of shadcn controls have opacity 0.
      return r.width > 2 && r.height > 2 && style.visibility !== 'hidden' && style.opacity !== '0' && !(e as HTMLInputElement).hidden
    })
    const problems: string[] = []
    for (const e of controls) {
      const r = e.getBoundingClientRect()
      if (inScrollFrame(e)) continue
      const dialog = e.closest('[role=dialog]')
      const right = dialog ? dialog.getBoundingClientRect().right : document.documentElement.clientWidth
      const left = dialog ? dialog.getBoundingClientRect().left : 0
      if (r.right > right + 1 || r.left < left - 1) problems.push(`${name(e)} sticks out of the ${dialog ? 'dialog' : 'window'}`)
    }
    // Inputs must show their value or placeholder in full; a date input needs room for a date and its picker icon.
    const ctx = document.createElement('canvas').getContext('2d')!
    for (const e of controls) {
      if (!(e instanceof HTMLInputElement) || !['text', 'search', 'date', 'number', ''].includes(e.type)) continue
      const style = getComputedStyle(e)
      ctx.font = `${style.fontWeight} ${style.fontSize} ${style.fontFamily}`
      const text = e.type === 'date' ? '00.00.0000' : e.value || e.placeholder
      const needed = ctx.measureText(text).width + (e.type === 'date' ? 24 : 0)
      const available = e.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight)
      if (needed > available + 1) problems.push(`${name(e)} is too narrow (${Math.round(available)} of ${Math.round(needed)} px)`)
    }
    for (let i = 0; i < controls.length; i++) {
      for (let j = i + 1; j < controls.length; j++) {
        const a = controls[i]
        const b = controls[j]
        if (a.contains(b) || b.contains(a)) continue
        const ra = a.getBoundingClientRect()
        const rb = b.getBoundingClientRect()
        const x = Math.min(ra.right, rb.right) - Math.max(ra.left, rb.left)
        const y = Math.min(ra.bottom, rb.bottom) - Math.max(ra.top, rb.top)
        if (x > 3 && y > 3) problems.push(`${name(a)} overlaps ${name(b)}`)
      }
    }
    return problems
  }, root)
}
