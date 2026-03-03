(function () {
  var content = document.querySelector('.content');
  if (!content) return;

  var headings = content.querySelectorAll('h2');
  if (headings.length < 2) return;

  /* ── 生成 slug ID ─────────────────────────────── */
  function slugify(text) {
    return text
      .trim()
      .replace(/[\s/··]+/g, '-')
      .replace(/[^\w\u4e00-\u9fff-]/g, '')
      .toLowerCase() || 'section';
  }

  var used = {};
  headings.forEach(function (h) {
    if (!h.id) {
      var base = slugify(h.textContent);
      var id = base, n = 1;
      while (used[id]) { id = base + '-' + (n++); }
      used[id] = true;
      h.id = id;
    } else {
      used[h.id] = true;
    }
  });

  /* ── 构建 TOC 节点 ─────────────────────────────── */
  var toc = document.createElement('nav');
  toc.className = 'page-toc';

  var title = document.createElement('div');
  title.className = 'toc-heading';
  title.textContent = '本页目录';
  toc.appendChild(title);

  var ul = document.createElement('ul');
  ul.className = 'toc-list';

  headings.forEach(function (h) {
    var li = document.createElement('li');
    var a = document.createElement('a');
    a.href = '#' + h.id;
    a.textContent = h.textContent.trim();
    li.appendChild(a);
    ul.appendChild(li);
  });

  toc.appendChild(ul);
  document.body.appendChild(toc);

  /* ── 滚动高亮当前章节 ────────────────────────── */
  var links = toc.querySelectorAll('a');

  function updateActive() {
    var current = null;
    headings.forEach(function (h) {
      if (h.getBoundingClientRect().top <= 88) {
        current = h.id;
      }
    });
    links.forEach(function (a) {
      var active = a.getAttribute('href') === '#' + current;
      if (active) a.classList.add('toc-active');
      else a.classList.remove('toc-active');
    });
  }

  document.addEventListener('scroll', updateActive, { passive: true });
  updateActive();
})();
