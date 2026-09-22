/* RitFit M1 PRO product page — v3 refinements.
   Loads AFTER the inline page script, ritfit-product-enhancements.js and ritfit-v2.js and
   only adds behaviour on top of them. Reuses the page globals (D, money, escapeHtml,
   options, variant, addToCart). Nothing in the original or v2 files is modified. */
(() => {
  'use strict';

  const q = (selector, root = document) => root.querySelector(selector);
  const qa = (selector, root = document) => [...root.querySelectorAll(selector)];
  const data = typeof D === 'object' && D ? D : JSON.parse(q('#page-data')?.textContent || '{}');
  const fmt = typeof money === 'function'
    ? money
    : (cents) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(cents / 100);
  const fmtCompact = (cents) => (cents % 100 === 0 ? `$${(cents / 100).toLocaleString('en-US')}` : fmt(cents));
  const esc = typeof escapeHtml === 'function'
    ? escapeHtml
    : (value) => String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const el = (tag, className, html) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (html != null) node.innerHTML = html;
    return node;
  };
  const setText = (node, text) => { if (node && node.textContent !== text) node.textContent = text; };
  const mobile = window.matchMedia('(max-width: 680px)');
  const currentVariant = () => (typeof variant === 'object' && variant ? variant : data.variants?.[0]);
  const currentOptions = () => (typeof options !== 'undefined' && Array.isArray(options) ? options : ['Black', 'Black', 'No Plates']);

  const KEEP = /^(M\d|PRO|Pro|3D|[A-Z]{2,4}\d{1,2}[A-Z]?|HSA\/FSA|FAQs?|RitFit|Smith|J-hooks?|US|USA)$/;
  const sentenceCase = (text) => {
    const lowered = String(text ?? '').trim()
      .split(/(\s+)/)
      .map((w) => (!/\S/.test(w) || KEEP.test(w.replace(/^[("]+|[)",.:;!?]+$/g, '')) ? w : w.toLowerCase()))
      .join('');
    return lowered.replace(/^([^\p{L}]*)(\p{L})/u, (m, prefix, letter) => prefix + letter.toUpperCase());
  };
  const PACKAGE_LABEL = { 'No Plates': 'No plates', '260lb Set': '260 lb set', '370lb Set': '370 lb set' };

  /* ------------------------------------------------------------------ */
  /* 1. Removals that must run after the enhancement script               */
  /* ------------------------------------------------------------------ */
  // The "Measure the room before delivery" module is what remains of #specs once the
  // enhancement script has moved the spec tables into #specifications.
  if (q('#specifications')) q('#specs')?.remove();
  q('#setup')?.remove();
  q('#features .stats')?.remove();

  const includedHeading = q('#included .section-heading h2');
  if (includedHeading) includedHeading.textContent = 'Three products in every package.';

  /* ------------------------------------------------------------------ */
  /* 2. Section nav: anchors and scroll spy                               */
  /* ------------------------------------------------------------------ */
  qa('.section-links a[href="#specs"]').forEach((a) => { a.setAttribute('href', '#specifications'); });
  qa('.rf-fit a[href="#specs"]').forEach((a) => { a.setAttribute('href', '#specifications'); });
  const navLinks = qa('.section-links a').filter((a) => q(a.getAttribute('href')));
  qa('.section-links a').forEach((a) => { if (!navLinks.includes(a)) a.remove(); });
  if ('IntersectionObserver' in window && navLinks.length) {
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        const href = `#${entry.target.id}`;
        if (!navLinks.some((a) => a.getAttribute('href') === href)) return;
        navLinks.forEach((a) => a.classList.toggle('active', a.getAttribute('href') === href));
      });
    }, { rootMargin: '-10% 0px -70% 0px', threshold: 0 });
    qa('#product, main > .section').forEach((section) => observer.observe(section));
  }

  /* ------------------------------------------------------------------ */
  /* 3. Savings badge above the title (from compare-at price only)        */
  /* ------------------------------------------------------------------ */
  const title = q('.purchase > h1');
  const badges = title ? el('div', 'rf-badges') : null;
  if (badges) title.before(badges);
  const renderBadges = () => {
    if (!badges) return;
    const v = currentVariant();
    const saving = v && v.compare_at_price > v.price ? v.compare_at_price - v.price : 0;
    badges.innerHTML = saving ? `<span class="rf-badge">Save ${esc(fmtCompact(saving))}</span>` : '';
    badges.hidden = !saving;
  };

  /* ------------------------------------------------------------------ */
  /* 4. "Fits your space" becomes a quick-details row under the ATC       */
  /* ------------------------------------------------------------------ */
  const fit = q('.rf-fit');
  const quickDetails = qa('.purchase > .quick-details');
  if (fit && quickDetails.length) {
    const details = el('details', 'quick-details rf-fit-details', '<summary>Fits your space</summary><div class="quick-content rf-fit-content"></div>');
    const content = q('.quick-content', details);
    const list = q('dl', fit);
    const link = q('a', fit);
    if (list) content.append(list);
    if (link) content.append(link);
    quickDetails[0].after(details);
    fit.remove();
  }

  /* ------------------------------------------------------------------ */
  /* 5. Trust items become pills under the add-ons                        */
  /* ------------------------------------------------------------------ */
  const trust = q('.rf-trust');
  if (trust) {
    trust.classList.add('rf-pills');
    qa('svg', trust).forEach((icon) => icon.remove());
    if (!q('a[href*="has-fsa"]', trust)) {
      trust.append(el('li', '', '<a href="https://www.ritfitsports.com/pages/has-fsa"><span>HSA/FSA eligible</span></a>'));
    }
    const anchor = q('.rf-addons') || q('#packages')?.closest('fieldset');
    if (anchor) anchor.after(trust);
  }

  /* ------------------------------------------------------------------ */
  /* 6. Add-to-cart panel (inline) and the sticky bar share one component */
  /* ------------------------------------------------------------------ */
  const PRICES_HTML = '<span class="rf-atc-prices"><del data-compare></del><b data-price></b></span>';
  const configLabel = () => {
    const [rack, bench, pkg] = currentOptions();
    return `${rack} rack · ${bench} bench · ${PACKAGE_LABEL[pkg] || sentenceCase(pkg)}`;
  };
  const buyRow = q('.buy-row');
  const addButton = q('#add-cart');
  if (buyRow && addButton) {
    const panel = el('div', 'rf-atc', '<p class="rf-atc-config" data-config></p>');
    buyRow.before(panel);
    panel.append(buyRow);
    buyRow.classList.add('rf-atc-row');
    addButton.classList.add('rf-atc-button');
    addButton.innerHTML = `<span class="rf-atc-label">Add to Cart</span>${PRICES_HTML}`;
    const shipNote = q('.rf-ship-note');
    if (shipNote) panel.after(shipNote);
  }

  const sticky = q('.mobile-sticky');
  if (sticky) {
    // Keep the original [data-add] button: the inline script and v2 bound their handlers to it.
    const stickyButton = q('[data-add]', sticky);
    const wrap = el('div', 'rf-atc rf-atc--sticky', '<p class="rf-atc-config" data-config></p><span class="rf-atc-out"><del data-compare></del><strong data-price></strong></span>');
    if (stickyButton) {
      stickyButton.classList.add('rf-atc-button');
      stickyButton.innerHTML = `<span class="rf-atc-label">Add to Cart</span>${PRICES_HTML}`;
      wrap.append(stickyButton);
    }
    [...sticky.children].forEach((child) => child.remove());
    sticky.append(wrap);
  }

  const renderAtc = () => {
    const v = currentVariant();
    if (!v) return;
    const label = configLabel();
    const hasCompare = v.compare_at_price > v.price;
    qa('[data-config]').forEach((node) => setText(node, label));
    qa('[data-compare]').forEach((node) => { setText(node, hasCompare ? fmt(v.compare_at_price) : ''); node.hidden = !hasCompare; });
    qa('[data-price]').forEach((node) => setText(node, fmt(v.price)));
    renderBadges();
  };
  const priceNode = q('.purchase .price');
  if (priceNode) new MutationObserver(renderAtc).observe(priceNode, { childList: true, characterData: true, subtree: true });
  renderAtc();

  /* ------------------------------------------------------------------ */
  /* 7. Features: one horizontal carousel on mobile, grid + disclosure on desktop */
  /* ------------------------------------------------------------------ */
  const featureGrid = q('#features .feature-grid');
  const moreContent = q('#features .rf-more-content');
  const moreGrid = moreContent && q('.rf-more-grid', moreContent);
  const syncFeatures = () => {
    if (!featureGrid || !moreGrid) return;
    if (mobile.matches) {
      [...moreGrid.children].forEach((card) => featureGrid.append(card));
      moreContent.hidden = true;
    } else if (moreContent.hidden) {
      [...featureGrid.children].slice(3).forEach((card) => moreGrid.append(card));
      moreContent.hidden = false;
    }
  };
  mobile.addEventListener('change', syncFeatures);
  syncFeatures();

  /* ------------------------------------------------------------------ */
  /* 8. FAQ: categories (from page-data groups) with question accordions   */
  /* ------------------------------------------------------------------ */
  const ICON = {
    rack: '<rect x="5" y="3" width="4" height="18" rx="1"/><rect x="15" y="3" width="4" height="18" rx="1"/><path d="M9 8h6M9 16h6"/>',
    smith: '<path d="M2 12h3M19 12h3M8 12h8"/><rect x="5" y="7" width="3" height="10" rx="1"/><rect x="16" y="7" width="3" height="10" rx="1"/>',
    cable: '<circle cx="12" cy="7" r="3.5"/><path d="M8.5 7H4M15.5 7H20M12 10.5V21M9 21h6"/>',
    hook: '<path d="M8 4h8M12 4v9a4 4 0 0 1-8 0"/>',
    plates: '<circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="2.5"/>',
    attachments: '<rect x="3" y="7" width="18" height="13" rx="2"/><path d="M12 10v7M8.5 13.5h7"/>',
    assembly: '<path d="M14.5 6.5a4 4 0 0 0-5 5L4 17l3 3 5.5-5.5a4 4 0 0 0 5-5l-2.5 2.5-2-2Z"/>',
    shipping: '<path d="M2 5h12v12H2zM14 9h4l4 4v4h-8"/><circle cx="6" cy="18" r="2"/><circle cx="18" cy="18" r="2"/>',
    compatibility: '<path d="M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1 1"/><path d="M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1-1"/>',
    warranty: '<path d="m12 2 8 4v6c0 5-8 10-8 10S4 17 4 12V6l8-4Z"/><path d="m8 12 3 3 5-6"/>',
    other: '<circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.7.4-1 .9-1 1.7M12 17h.01"/>'
  };
  const iconFor = (name) => {
    const t = name.toLowerCase();
    const key = /rack|frame|upright/.test(t) ? 'rack'
      : /smith/.test(t) ? 'smith'
        : /cable|pulley/.test(t) ? 'cable'
          : /hook|spotter|safety/.test(t) ? 'hook'
            : /plate|storage/.test(t) ? 'plates'
              : /attach|accessor/.test(t) ? 'attachments'
                : /assembl|setup|install/.test(t) ? 'assembly'
                  : /ship|deliver|packag/.test(t) ? 'shipping'
                    : /compat|custom|using/.test(t) ? 'compatibility'
                      : /warrant|mainten|return|buy/.test(t) ? 'warranty' : 'other';
    return `<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">${ICON[key]}</svg>`;
  };
  const keywordCategory = (item) => {
    const t = `${item.q || ''} ${item.a || ''}`.toLowerCase();
    if (/assembl|install|tool|setup|set up/.test(t)) return 'Setup & assembly';
    if (/ship|deliver|box|carton|packag/.test(t)) return 'Shipping & delivery';
    if (/warrant|return|refund|mainten/.test(t)) return 'Warranty & returns';
    if (/compatib|attach|accessor|fit|dimension|capacity|weight|lb|material|spec/.test(t)) return 'Specs & compatibility';
    if (/exercise|workout|train|use|adjust|pulley|smith|cable/.test(t)) return 'Using the machine';
    return 'Other';
  };
  const faqGroups = (() => {
    const raw = Array.isArray(data.faq) ? data.faq : [];
    if (raw.length && raw[0] && Array.isArray(raw[0].items)) {
      return raw.filter((g) => g.items.length).map((g) => ({ title: sentenceCase(g.title || 'Other'), items: g.items }));
    }
    const buckets = new Map();
    raw.forEach((item) => {
      const name = item.category || item.group || keywordCategory(item);
      if (!buckets.has(name)) buckets.set(name, []);
      buckets.get(name).push(item);
    });
    return [...buckets].map(([titleText, items]) => ({ title: sentenceCase(titleText), items }));
  })();

  const faqLayout = q('#faq .faq-layout');
  if (faqLayout && faqGroups.length) {
    const contactLink = q('a.text-button', faqLayout);
    const countLabel = (n) => `${n} ${n === 1 ? 'question' : 'questions'}`;
    faqLayout.innerHTML = `
      <div class="rf-faq">
        <input class="faq-search rf-faq-search" type="search" placeholder="Search questions" aria-label="Search questions" autocomplete="off">
        <p class="rf-faq-empty" hidden>No questions match your search.</p>
        <div class="rf-faq-categories">${faqGroups.map((group, gi) => `
          <details class="rf-faq-category" data-count="${group.items.length}">
            <summary>
              <span class="rf-faq-icon">${iconFor(group.title)}</span>
              <span class="rf-faq-title"><strong>${esc(group.title)}</strong><small data-count-label>${countLabel(group.items.length)}</small></span>
              <span class="rf-faq-chevron" aria-hidden="true"></span>
            </summary>
            <div class="rf-faq-body">${group.items.map((item, qi) => `
              <details class="faq-item" id="faq-${gi + 1}-${qi + 1}">
                <summary>${esc(item.q)}</summary>
                <p>${esc(item.a)}</p>
              </details>`).join('')}
            </div>
          </details>`).join('')}
        </div>
      </div>`;
    const faqRoot = q('.rf-faq', faqLayout);
    if (contactLink) faqRoot.append(contactLink);

    const search = q('.rf-faq-search', faqRoot);
    const empty = q('.rf-faq-empty', faqRoot);
    const categories = qa('.rf-faq-category', faqRoot);
    const applySearch = () => {
      const query = search.value.trim().toLowerCase();
      let total = 0;
      categories.forEach((category) => {
        const items = qa('.faq-item', category);
        let matches = 0;
        items.forEach((item) => {
          const match = !query || item.textContent.toLowerCase().includes(query);
          item.hidden = !match;
          if (query) item.open = match; else item.open = false;
          if (match) matches += 1;
        });
        category.hidden = query ? matches === 0 : false;
        category.open = query ? matches > 0 : false;
        setText(q('[data-count-label]', category), query ? `${matches} of ${items.length} match` : countLabel(items.length));
        total += matches;
      });
      empty.hidden = total > 0;
    };
    search.addEventListener('input', applySearch);
  }

  document.documentElement.classList.add('rf-v3');
})();
