/* RitFit M1 PRO product page — v2 refinements.
   Loads AFTER the inline page script and ritfit-product-enhancements.js and only adds
   behaviour on top of them. It reads the same #page-data JSON and reuses the page
   globals defined by the inline script (D, money, escapeHtml, options, variant, cart,
   renderCart). Nothing in the original files is modified. */
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

  /* ------------------------------------------------------------------ */
  /* 1. Text formatting helpers (brand casing rules, applied at render)   */
  /* ------------------------------------------------------------------ */
  // Words that keep their original casing: model codes and proper nouns.
  const KEEP = /^(M\d|PRO|Pro|3D|[A-Z]{2,4}\d{1,2}[A-Z]?|HSA\/FSA|FAQs?|RitFit|Smith|YouTube|Truemed|Affirm|Afterpay|Olympic|US|USA|PDF|GAZELLE|BUFFALO|GATOR|L×W×H)$/;
  const SMALL = /^(a|an|and|as|at|but|by|for|in|of|on|or|the|to|vs|with)$/i;
  const stripPunct = (word) => word.replace(/^[("'“]+|[)"'”,.:;!?]+$/g, '');
  const noEmoji = (text) => String(text ?? '').replace(/[\p{Extended_Pictographic}️]/gu, '').replace(/\s+/g, ' ').trim();
  const brandWords = (text) => String(text).replace(/\bRITFIT\b/g, 'RitFit').replace(/\bSERIES\b/g, 'Series');
  const fixUnits = (text) => String(text).replace(/(\d)\s*LBS?\b/g, '$1 lb').replace(/(\d)\s*lbs\b/g, '$1 lb');

  const sentenceCase = (text) => {
    const lowered = brandWords(fixUnits(noEmoji(text)))
      .split(/(\s+)/)
      .map((w) => (!/\S/.test(w) || KEEP.test(stripPunct(w)) ? w : w.toLowerCase()))
      .join('');
    return lowered.replace(/^([^\p{L}]*)(\p{L})/u, (m, prefix, letter) => prefix + letter.toUpperCase());
  };

  const titleCase = (text) => brandWords(noEmoji(text))
    .split(/\s+/)
    .map((w, i) => {
      if (KEEP.test(stripPunct(w))) return w;
      if (i > 0 && SMALL.test(w)) return w.toLowerCase();
      return w.charAt(0).toUpperCase() + w.slice(1).toLowerCase();
    })
    .join(' ');

  /* ------------------------------------------------------------------ */
  /* 2. Capitalisation pass on rendered text                              */
  /* ------------------------------------------------------------------ */
  // Mobile menu: rebuilt from page-data header links with Title Case and no emoji.
  const menu = q('#menu-links');
  if (menu && Array.isArray(data.header)) {
    const seen = new Map();
    data.header.forEach((item) => {
      const label = titleCase(item.text);
      if (!label || label.length >= 40 || /quick|cart|search|account/i.test(label) || seen.has(label)) return;
      seen.set(label, item.url);
    });
    menu.innerHTML = [...seen].map(([label, url]) => `<a href="${esc(url)}">${esc(label)}</a>`).join('');
  }
  // Mega-menu product names: only brand-word fixes (product names stay as the store writes them).
  qa('.nav-product strong').forEach((node) => { node.textContent = brandWords(node.textContent); });

  /* ------------------------------------------------------------------ */
  /* 3. Package option cards                                              */
  /* ------------------------------------------------------------------ */
  const PACKAGE_COPY = {
    'No Plates': { label: 'No plates', desc: 'Machine, 264 lb weight stack, bench' },
    '260lb Set': { label: '260 lb set', desc: 'Adds 10 plates: 10–45 lb pairs' },
    '370lb Set': { label: '370 lb set', desc: 'Adds 12 plates: 10–55 lb pairs' }
  };
  const currentOptions = () => (typeof options !== 'undefined' && Array.isArray(options) ? options : ['Black', 'Black', 'No Plates']);
  const packageVariant = (value) => {
    const [rack, bench] = currentOptions();
    return (data.variants || []).find((v) => v.options?.[0] === rack && v.options?.[1] === bench && v.options?.[2] === value);
  };
  const packageLabel = (value) => PACKAGE_COPY[value]?.label || sentenceCase(value);

  const renderPackages = () => {
    const buttons = qa('#packages .package');
    if (!buttons.length) return;
    const prices = buttons.map((b) => packageVariant(b.dataset.value)?.price).filter((p) => typeof p === 'number');
    const base = prices.length ? Math.min(...prices) : 0;
    buttons.forEach((button) => {
      const v = packageVariant(button.dataset.value);
      const copy = PACKAGE_COPY[button.dataset.value] || { label: sentenceCase(button.dataset.value), desc: '' };
      const diff = v ? v.price - base : 0;
      const available = Boolean(v && v.available !== false);
      button.classList.add('rf-package');
      button.innerHTML = `
        <span class="rf-package-head">
          <span class="rf-package-name">${esc(copy.label)}</span>
          <span class="rf-package-diff">${diff > 0 ? `+${esc(fmtCompact(diff))}` : 'Base price'}</span>
        </span>
        ${copy.desc ? `<span class="rf-package-desc">${esc(copy.desc)}</span>` : ''}
        <span class="rf-package-stock ${available ? 'is-available' : 'is-unavailable'}">${available ? 'In stock' : 'Out of stock'}</span>`;
    });
  };

  /* ------------------------------------------------------------------ */
  /* 4. Financing: monthly figure follows the selected variant            */
  /* ------------------------------------------------------------------ */
  // The source page quotes $216.62/mo for the $2,399.99 base variant. Other variants
  // are scaled from that same quote so the base figure is kept exactly.
  const MONTHLY_RATE = 216.62 / 2399.99;
  const renderMonthly = () => {
    const output = q('[data-monthly]');
    const current = typeof variant === 'object' && variant ? variant : data.variants?.[0];
    if (output && current) output.textContent = fmt(Math.round(current.price * MONTHLY_RATE));
  };

  /* ------------------------------------------------------------------ */
  /* 5. Key highlights + Fits your space (from page-data)                 */
  /* ------------------------------------------------------------------ */
  const ICONS = [
    '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="7" r="3.5"/><path d="M8.5 7H4M15.5 7H20M12 10.5V21M9 21h6"/></svg>',
    '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M2 12h3M19 12h3M8 12h8"/><rect x="5" y="7" width="3" height="10" rx="1"/><rect x="16" y="7" width="3" height="10" rx="1"/></svg>',
    '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M3 5h18M6 5v16M18 5v16M9 12h6"/></svg>',
    '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><rect x="9" y="3" width="6" height="18" rx="1"/><circle cx="12" cy="7.5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="16.5" r="1"/></svg>'
  ];
  const finance = q('.rf-finance');
  if (finance) {
    const highlights = (data.features || []).slice(0, 4).map((f) => sentenceCase(f.title)).filter(Boolean);
    let anchor = finance;
    if (highlights.length) {
      const block = el('section', 'rf-highlights', `
        <h2 class="rf-block-title">Key highlights</h2>
        <ul>${highlights.map((t, i) => `<li>${ICONS[i % ICONS.length]}<span>${esc(t)}</span></li>`).join('')}</ul>`);
      anchor.after(block);
      anchor = block;
    }
    const specRows = (data.specs || []).flatMap((group) => group.rows || []);
    const spec = (pattern) => {
      const row = specRows.find((r) => pattern.test(String(r[0] || '')));
      return row && row[1] ? fixUnits(row[1]).trim() : '';
    };
    const width = spec(/^rack width/i);
    const length = spec(/^rack length/i);
    const area = spec(/foot ?print/i);
    const capacity = spec(/^frame capacity/i);
    const fit = [];
    if (width && length) fit.push(['Footprint', `${width} W × ${length} D`]);
    if (area) fit.push(['Floor area', area]);
    if (capacity) fit.push(['Frame capacity', capacity]);
    if (fit.length) {
      anchor.after(el('section', 'rf-fit', `
        <h2 class="rf-block-title">Fits your space</h2>
        <dl>${fit.map(([label, value]) => `<div><dt>${esc(label)}</dt><dd>${esc(value)}</dd></div>`).join('')}</dl>
        <a href="#specs">See full space requirements</a>`));
    }
  }

  /* ------------------------------------------------------------------ */
  /* 6. Add to your setup (add-ons, demo bag only)                        */
  /* ------------------------------------------------------------------ */
  const absoluteUrl = (url) => {
    if (!url) return '';
    if (url.startsWith('//')) return `https:${url}`;
    if (url.startsWith('/')) return `https://www.ritfitsports.com${url.split('?')[0]}`;
    return url;
  };
  const thumb = (url) => (url ? `${url}${url.includes('?') ? '&' : '?'}width=200` : '');
  const toCents = (price) => (typeof price === 'number' ? price : Math.round(parseFloat(String(price).replace(/[^0-9.]/g, '')) * 100) || 0);
  const isMachine = (title) => /smith machine|home gym|package|leg press|extension|hack squat/i.test(title);
  const candidates = [];
  (data.recommendations || []).forEach((item) => {
    if (isMachine(item.title)) return;
    candidates.push({ id: `rec-${item.id}`, title: brandWords(item.title), price: toCents(item.price), image: thumb(absoluteUrl(item.featured_image)), url: absoluteUrl(item.url) });
  });
  (data.related || [])
    .filter((item) => !isMachine(item.title) && !/\bplates?\b/i.test(item.title))
    .sort((a, b) => toCents(a.price) - toCents(b.price))
    .forEach((item, index) => {
      candidates.push({ id: `rel-${index}`, title: brandWords(item.title), price: toCents(item.price), image: item.image?.src || '', url: item.url });
    });
  const addons = candidates.filter((c) => c.image && c.price).slice(0, 3);
  const selectedAddons = new Set();
  const bagAddons = new Map();

  const syncAddonToggles = () => {
    qa('.rf-addon-toggle').forEach((button) => {
      const on = selectedAddons.has(button.dataset.addon);
      button.setAttribute('aria-pressed', String(on));
      button.textContent = on ? 'Added' : 'Add';
    });
  };

  const packagesFieldset = q('#packages')?.closest('fieldset');
  if (packagesFieldset && addons.length) {
    const block = el('section', 'rf-addons', `
      <h2 class="rf-block-title" id="rf-addons-title">Add to your setup</h2>
      <ul class="rf-addon-list">${addons.map((a) => `
        <li class="rf-addon">
          <img src="${esc(a.image)}" alt="${esc(a.title)}" width="56" height="56" loading="lazy" decoding="async">
          <div class="rf-addon-copy">
            <a href="${esc(a.url)}" target="_blank" rel="noopener">${esc(a.title)}</a>
            <span class="rf-addon-price">${esc(fmt(a.price))}</span>
          </div>
          <button type="button" class="rf-addon-toggle" aria-pressed="false" data-addon="${esc(a.id)}" aria-label="Add ${esc(a.title)} to your setup">Add</button>
        </li>`).join('')}</ul>`);
    block.setAttribute('aria-labelledby', 'rf-addons-title');
    packagesFieldset.after(block);
    block.addEventListener('click', (event) => {
      const button = event.target.closest('.rf-addon-toggle');
      if (!button) return;
      const id = button.dataset.addon;
      if (selectedAddons.has(id)) selectedAddons.delete(id); else selectedAddons.add(id);
      syncAddonToggles();
    });
  }

  // Bag dialog: keep the original renderer and append the add-on rows after it.
  if (typeof window.renderCart === 'function') {
    const baseRenderCart = window.renderCart;
    window.renderCart = function renderCartV2() {
      baseRenderCart.apply(this, arguments);
      const content = q('#cart-content');
      if (!content) return;
      const mainQuantity = typeof cart !== 'undefined' && cart ? cart.quantity : 0;
      const total = mainQuantity + bagAddons.size;
      const badge = q('#bag-count');
      if (badge) { badge.textContent = total; badge.hidden = !total; }
      const titleCount = q('#cart-title-count');
      if (titleCount) titleCount.textContent = total ? `(${total})` : '';
      if (!bagAddons.size) return;
      if (!mainQuantity) content.innerHTML = '';
      const list = el('div', 'rf-cart-addons', `
        <p class="rf-cart-addons-title">Add-ons <span>· added locally in this preview</span></p>
        ${[...bagAddons.values()].map((a) => `
          <div class="rf-cart-addon">
            <img src="${esc(a.image)}" alt="${esc(a.title)}">
            <div><strong>${esc(a.title)}</strong><span>${esc(fmt(a.price))}</span></div>
            <button type="button" class="text-button" data-remove-addon="${esc(a.id)}" aria-label="Remove ${esc(a.title)}">Remove</button>
          </div>`).join('')}`);
      content.append(list);
      qa('[data-remove-addon]', list).forEach((button) => button.addEventListener('click', () => {
        bagAddons.delete(button.dataset.removeAddon);
        selectedAddons.delete(button.dataset.removeAddon);
        syncAddonToggles();
        window.renderCart();
      }));
    };
  }

  const commitAddons = () => {
    if (typeof variant === 'object' && variant && variant.available === false) return;
    selectedAddons.forEach((id) => {
      const item = addons.find((a) => a.id === id);
      if (item) bagAddons.set(id, item);
    });
    if (typeof window.renderCart === 'function') window.renderCart();
  };
  q('#add-cart')?.addEventListener('click', commitAddons);
  qa('[data-add]').forEach((button) => button.addEventListener('click', commitAddons));

  /* ------------------------------------------------------------------ */
  /* 7. Sticky buy bar copy                                               */
  /* ------------------------------------------------------------------ */
  const stickyLabel = q('.mobile-sticky small');
  const syncSticky = () => {
    if (!stickyLabel) return;
    const [rack, , pkg] = currentOptions();
    const want = `${rack} rack · ${packageLabel(pkg)}`;
    if (stickyLabel.textContent !== want) stickyLabel.textContent = want;
  };
  if (stickyLabel) new MutationObserver(syncSticky).observe(stickyLabel, { childList: true, characterData: true, subtree: true });

  // Re-render variant-dependent pieces whenever the inline script updates the price.
  const priceNode = q('.purchase .price');
  const syncVariant = () => { renderPackages(); renderMonthly(); syncSticky(); };
  if (priceNode) new MutationObserver(syncVariant).observe(priceNode, { childList: true, characterData: true, subtree: true });
  syncVariant();

  /* ------------------------------------------------------------------ */
  /* 8. Real setups section (review photos from page-data)                */
  /* ------------------------------------------------------------------ */
  const experiences = q('#experiences');
  if (experiences && !q('#real-setups')) {
    const shots = (data.reviews || [])
      .filter((r) => r.image && r.image.src)
      .map((r) => ({
        src: r.image.src,
        alt: r.image.alt || `Customer photo of the RitFit M1 PRO shared by ${r.name}`,
        caption: [r.name, r.rating].filter(Boolean).join(' · '),
        video: /video/i.test(r.image.alt || '')
      }));
    let tiles = [...shots.filter((s) => !s.video), ...shots.filter((s) => s.video)].slice(0, 8);
    let intro = 'Photos shared in customer reviews of the M1 PRO. Tap a photo to enlarge it.';
    if (tiles.length < 4) {
      tiles = (data.media || [])
        .filter((m) => m.media_type === 'image' && m.src && /home gym setting|gym floor|for strength training|workouts/i.test(m.alt || ''))
        .slice(0, 8)
        .map((m) => ({ src: m.src, alt: m.alt, caption: '' }));
      intro = 'Lifestyle photos of the M1 PRO from the product gallery.';
    }
    if (tiles.length) {
      const section = el('section', 'section', `
        <div class="wrap">
          <div class="section-heading">
            <div><h2>Real setups from RitFit owners</h2></div>
            <p>${esc(intro)}</p>
          </div>
          <ul class="rf-setups-grid">${tiles.map((t) => `
            <li>
              <figure>
                <button type="button" class="rf-setup-tile" data-src="${esc(t.src)}" data-alt="${esc(t.alt)}" aria-label="Enlarge photo: ${esc(t.alt)}">
                  <img src="${esc(t.src)}" alt="${esc(t.alt)}" loading="lazy" decoding="async">
                </button>
                ${t.caption ? `<figcaption>${esc(t.caption)}</figcaption>` : ''}
              </figure>
            </li>`).join('')}</ul>
        </div>`);
      section.id = 'real-setups';
      experiences.after(section);
      const zoom = q('#zoom-dialog');
      const zoomImage = q('#zoom-image');
      section.addEventListener('click', (event) => {
        const tile = event.target.closest('.rf-setup-tile');
        if (!tile || !zoom || !zoomImage) return;
        zoomImage.src = tile.dataset.src;
        zoomImage.alt = tile.dataset.alt;
        zoom.showModal();
      });
    }
  }

  /* ------------------------------------------------------------------ */
  /* 9. Section order + section nav                                       */
  /* ------------------------------------------------------------------ */
  const ORDER = ['features', 'specs', 'specifications', 'included', 'details', 'compare', 'shipping', 'experiences', 'real-setups', 'faq', 'reviews', 'setup'];
  const main = q('main');
  const footer = q('main > .footer');
  if (main) {
    ORDER.forEach((id) => { const section = q(`#${id}`); if (section) main.append(section); });
    if (footer) main.append(footer);
  }

  const NAV = [
    ['product', 'Configurations'], ['features', 'Features'], ['specs', 'Gear specs'], ['included', "What's included"],
    ['details', 'More details'], ['compare', 'Compare'], ['shipping', 'Shipping'], ['experiences', 'Real experiences'],
    ['real-setups', 'Real setups'], ['faq', 'FAQ'], ['reviews', 'Reviews']
  ].filter(([id]) => Boolean(q(`#${id}`)));
  const links = q('.section-links');
  if (links) {
    links.innerHTML = NAV.map(([id, label]) => `<a href="#${id}">${esc(label)}</a>`).join('');
    const navLinks = qa('a', links);
    const alias = { specifications: 'specs' };
    const setActive = (id) => navLinks.forEach((a) => a.classList.toggle('active', a.getAttribute('href') === `#${id}`));
    if ('IntersectionObserver' in window) {
      const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          const id = alias[entry.target.id] || entry.target.id;
          if (navLinks.some((a) => a.getAttribute('href') === `#${id}`)) setActive(id);
        });
      }, { rootMargin: '-10% 0px -70% 0px', threshold: 0 });
      qa('#product, main > .section').forEach((section) => observer.observe(section));
    }
  }

  document.documentElement.classList.add('rf-v2');
})();
