// The screen picker under the TV: a tab list, moved with the arrow keys like a remote.
(() => {
  const list = document.querySelector('[data-picker]');
  if (!list) return;
  const tabs = [...list.querySelectorAll('[role="tab"]')];
  const image = document.getElementById('tv-image');
  const caption = document.getElementById('tv-caption');

  const select = (tab) => {
    if (tab.getAttribute('aria-selected') === 'true') return;
    tabs.forEach((t) => {
      const on = t === tab;
      t.setAttribute('aria-selected', on);
      t.tabIndex = on ? 0 : -1;
    });
    const next = new Image();
    next.src = tab.dataset.src;
    next.decode().catch(() => {}).then(() => {
      image.src = next.src;
      image.alt = tab.dataset.alt;
      caption.textContent = tab.dataset.caption;
    });
  };

  tabs.forEach((tab) => tab.addEventListener('click', () => select(tab)));
  list.addEventListener('keydown', (e) => {
    const i = tabs.indexOf(document.activeElement);
    if (i < 0) return;
    const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[e.key];
    const to = step ? (i + step + tabs.length) % tabs.length : e.key === 'Home' ? 0 : e.key === 'End' ? tabs.length - 1 : -1;
    if (to < 0) return;
    e.preventDefault();
    tabs[to].focus();
    select(tabs[to]);
  });
})();
