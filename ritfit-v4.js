/* RitFit M1 PRO product page — v4 refinements.
   Loads AFTER the inline page script, ritfit-product-enhancements.js, ritfit-v2.js and
   ritfit-v3.js and only adds behaviour on top of them. Reuses the page globals
   (D, money, escapeHtml, options, variant, stickyObserver). Nothing in the original,
   v2 or v3 files is modified. */
(() => {
  'use strict';

  const q = (selector, root = document) => root.querySelector(selector);
  const qa = (selector, root = document) => [...root.querySelectorAll(selector)];
  const data = typeof D === 'object' && D ? D : JSON.parse(q('#page-data')?.textContent || '{}');
  const fmt = typeof money === 'function'
    ? money
    : (cents) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(cents / 100);
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
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const currentVariant = () => (typeof variant === 'object' && variant ? variant : data.variants?.[0]);
  const currentOptions = () => (typeof options !== 'undefined' && Array.isArray(options) ? options : ['Black', 'Black', 'No Plates']);

  const KEEP = /^(M\d|PRO|Pro|3D|[A-Z]|[A-Z]{1,4}\d{1,2}[A-Z]?|HSA\/FSA|FAQs?|RitFit|Smith|J-hooks?|US|USA|L×W×H)$/; // single letters / codes keep carton names like "Weight stack A", "Box D1"
  const noEmoji = (text) => String(text ?? '').replace(/[\p{Extended_Pictographic}️]/gu, '').replace(/\s+/g, ' ').trim();
  const fixUnits = (text) => String(text ?? '').replace(/(\d)\s*LBS?\b/g, '$1 lb').replace(/(\d)\s*lbs\b/g, '$1 lb');
  const sentenceCase = (text) => {
    const lowered = fixUnits(noEmoji(text)).replace(/\bPKG\b/g, 'package')
      .split(/(\s+)/)
      .map((w) => (!/\S/.test(w) || KEEP.test(w.replace(/^[("]+|[)",.:;!?]+$/g, '')) ? w : w.toLowerCase()))
      .join('');
    return lowered.replace(/^([^\p{L}]*)(\p{L})/u, (m, prefix, letter) => prefix + letter.toUpperCase());
  };
  // Leading + trailing throttle on a timer (not requestAnimationFrame), so scroll/resize
  // work runs at once and keeps running even when frame callbacks are paused.
  const throttle = (fn, wait = 80) => {
    let last = 0;
    let timer = 0;
    return () => {
      const now = Date.now();
      if (now - last >= wait) {
        last = now;
        fn();
        return;
      }
      if (timer) return;
      timer = window.setTimeout(() => { timer = 0; last = Date.now(); fn(); }, wait - (now - last));
    };
  };
  const CHEVRON_LEFT = '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m15 5-7 7 7 7"/></svg>';
  const CHEVRON_RIGHT = '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m9 5 7 7-7 7"/></svg>';

  /* ------------------------------------------------------------------ */
  /* Shared: snap carousel with round prev/next buttons                  */
  /* ------------------------------------------------------------------ */
  const makeCarousel = (scroller, { heading, label }) => {
    if (!scroller || scroller.classList.contains('rf-carousel')) return null;
    scroller.classList.add('rf-carousel');
    scroller.setAttribute('tabindex', '0');
    scroller.setAttribute('aria-label', `${label} carousel`);

    const nav = el('div', 'rf-carousel-nav');
    const prev = el('button', 'rf-carousel-button rf-carousel-prev', CHEVRON_LEFT);
    const next = el('button', 'rf-carousel-button rf-carousel-next', CHEVRON_RIGHT);
    prev.type = 'button';
    next.type = 'button';
    prev.setAttribute('aria-label', `Previous ${label}`);
    next.setAttribute('aria-label', `Next ${label}`);
    nav.append(prev, next);
    if (heading) {
      heading.classList.add('rf-has-carousel');
      heading.append(nav);
    } else {
      scroller.before(nav);
    }

    const step = () => {
      const first = scroller.firstElementChild;
      const second = first?.nextElementSibling;
      if (first && second) return second.getBoundingClientRect().left - first.getBoundingClientRect().left;
      return scroller.clientWidth;
    };
    const go = (direction) => scroller.scrollBy({ left: direction * step(), behavior: reducedMotion.matches ? 'auto' : 'smooth' });
    prev.addEventListener('click', () => go(-1));
    next.addEventListener('click', () => go(1));

    const update = () => {
      const max = scroller.scrollWidth - scroller.clientWidth;
      prev.disabled = scroller.scrollLeft <= 1;
      next.disabled = scroller.scrollLeft >= max - 1;
      nav.hidden = max <= 1;
    };
    const scheduleUpdate = throttle(update);
    scroller.addEventListener('scroll', scheduleUpdate, { passive: true });
    window.addEventListener('resize', scheduleUpdate);
    if ('ResizeObserver' in window) new ResizeObserver(scheduleUpdate).observe(scroller);
    update();
    return { scroller, nav, update };
  };

  /* ------------------------------------------------------------------ */
  /* 1. Package cards show the absolute price for the chosen colours     */
  /* ------------------------------------------------------------------ */
  const packageVariant = (value) => {
    const [rack, bench] = currentOptions();
    return (data.variants || []).find((v) => v.options?.[0] === rack && v.options?.[1] === bench && v.options?.[2] === value);
  };
  const renderPackagePrices = () => {
    qa('#packages .package').forEach((button) => {
      const v = packageVariant(button.dataset.value);
      const output = q('.rf-package-diff', button);
      if (!output) return;
      output.classList.add('rf-package-price');
      setText(output, v ? fmt(v.price) : '');
    });
  };

  /* ------------------------------------------------------------------ */
  /* 2. Sticky add-to-cart bar: shown while the inline panel is off-screen */
  /* ------------------------------------------------------------------ */
  // The inline script's observer (threshold .5 on #add-cart and #packages) owns the
  // [hidden] attribute; disconnect it so one observer decides.
  if (typeof stickyObserver !== 'undefined' && stickyObserver && typeof stickyObserver.disconnect === 'function') {
    stickyObserver.disconnect();
  }
  const stickyBar = q('.mobile-sticky');
  const inlinePanel = q('.purchase .rf-atc') || q('.buy-row') || q('#add-cart');
  const packagesNode = q('#packages');
  const visibility = { panel: true, packages: true };
  const syncStickyBar = () => {
    if (!stickyBar) return;
    const show = !visibility.panel && (mobile.matches || !visibility.packages);
    if (stickyBar.hidden === !show) return;
    stickyBar.hidden = !show;
  };
  const measureVisibility = () => {
    if (!inlinePanel) return;
    const rect = inlinePanel.getBoundingClientRect();
    const viewportHeight = window.innerHeight || document.documentElement.clientHeight;
    visibility.panel = rect.bottom > 0 && rect.top < viewportHeight && rect.height > 0;
    if (packagesNode) {
      const p = packagesNode.getBoundingClientRect();
      const visibleHeight = Math.min(p.bottom, viewportHeight) - Math.max(p.top, 0);
      visibility.packages = p.height > 0 && visibleHeight / p.height >= 0.5;
    }
    syncStickyBar();
  };
  if (stickyBar && inlinePanel) {
    if ('IntersectionObserver' in window) {
      const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          if (entry.target === inlinePanel) visibility.panel = entry.isIntersecting;
          else if (entry.target === packagesNode) visibility.packages = entry.isIntersecting && entry.intersectionRatio >= 0.5;
        });
        syncStickyBar();
      }, { threshold: [0, 0.5] });
      observer.observe(inlinePanel);
      if (packagesNode) observer.observe(packagesNode);
    }
    // Scroll/resize fallback keeps the bar right even when observer callbacks are delayed.
    const scheduleMeasure = throttle(measureVisibility);
    window.addEventListener('scroll', scheduleMeasure, { passive: true });
    window.addEventListener('resize', scheduleMeasure);
    mobile.addEventListener('change', scheduleMeasure);
    measureVisibility();
  }

  /* ------------------------------------------------------------------ */
  /* 3. Trust pills: seamless marquee                                    */
  /* ------------------------------------------------------------------ */
  const pills = q('.rf-trust.rf-pills');
  if (pills && !pills.closest('.rf-marquee')) {
    const viewport = el('div', 'rf-marquee');
    const track = el('div', 'rf-marquee-track');
    pills.before(viewport);
    viewport.append(track);
    track.append(pills);
    const clone = pills.cloneNode(true);
    clone.classList.add('rf-pills-clone');
    clone.setAttribute('aria-hidden', 'true');
    qa('[id]', clone).forEach((node) => node.removeAttribute('id'));
    qa('a, button', clone).forEach((node) => { node.tabIndex = -1; });
    track.append(clone);
  }

  /* ------------------------------------------------------------------ */
  /* 4. Features: carousel at every size + custom video play/pause       */
  /* ------------------------------------------------------------------ */
  const featureGrid = q('#features .feature-grid');
  const featureMore = q('#features .rf-more-content');
  const featureMoreGrid = featureMore && q('.rf-more-grid', featureMore);
  const flattenFeatures = () => {
    if (!featureGrid) return;
    if (featureMoreGrid) [...featureMoreGrid.children].forEach((card) => featureGrid.append(card));
    if (featureMore) featureMore.hidden = true;
  };
  flattenFeatures();
  // v3 moves cards back into the disclosure when leaving the mobile breakpoint; this
  // listener is registered later, so it runs after v3's and flattens again.
  mobile.addEventListener('change', flattenFeatures);

  let featuresCarousel = null;
  if (featureGrid) {
    featuresCarousel = makeCarousel(featureGrid, { heading: q('#features .section-heading'), label: 'features' });
    mobile.addEventListener('change', () => featuresCarousel?.update());
  }

  const PLAY_ICON = '<svg class="rf-icon-play" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M8 5.5v13l10.5-6.5z"/></svg>';
  const PAUSE_ICON = '<svg class="rf-icon-pause" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M7 5h3.6v14H7zM13.4 5H17v14h-3.4z"/></svg>';
  const videoCards = qa('#features .feature-card').filter((card) => q('video', card));
  const videoState = new Map(); // card -> { video, button, manual: null | 'play' | 'pause' }
  const safePlay = (video) => {
    const result = video.play();
    if (result && typeof result.catch === 'function') result.catch(() => {});
  };
  videoCards.forEach((card) => {
    const video = q('video', card);
    const media = q('.feature-media', card) || video.parentElement;
    video.removeAttribute('controls');
    video.controls = false;
    video.muted = true;
    video.defaultMuted = true;
    video.loop = true;
    video.playsInline = true;
    video.setAttribute('muted', '');
    video.setAttribute('loop', '');
    video.setAttribute('playsinline', '');
    video.preload = 'metadata';
    video.setAttribute('preload', 'metadata');

    const button = el('button', 'rf-video-toggle', PLAY_ICON + PAUSE_ICON);
    button.type = 'button';
    button.setAttribute('aria-pressed', 'false');
    button.setAttribute('aria-label', 'Play video');
    media.append(button);
    const state = { video, button, manual: null };
    videoState.set(card, state);

    const syncButton = () => {
      const playing = !video.paused && !video.ended;
      button.setAttribute('aria-pressed', String(playing));
      button.setAttribute('aria-label', playing ? 'Pause video' : 'Play video');
    };
    video.addEventListener('play', syncButton);
    video.addEventListener('playing', syncButton);
    video.addEventListener('pause', syncButton);
    video.addEventListener('ended', syncButton);
    button.addEventListener('click', () => {
      if (video.paused) {
        state.manual = 'play';
        safePlay(video);
      } else {
        state.manual = 'pause';
        video.pause();
      }
      syncButton();
    });
  });
  if (videoCards.length && 'IntersectionObserver' in window) {
    const videoObserver = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        const state = videoState.get(entry.target);
        if (!state) return;
        if (!entry.isIntersecting) {
          // Fully out of view: pause and forget the manual override for this card.
          state.manual = null;
          if (!state.video.paused) state.video.pause();
        } else if (entry.intersectionRatio >= 0.6) {
          if (state.manual !== 'pause' && !reducedMotion.matches && state.video.paused) safePlay(state.video);
        } else if (state.manual !== 'play' && !state.video.paused) {
          state.video.pause();
        }
      });
    }, { threshold: [0, 0.6] });
    videoCards.forEach((card) => videoObserver.observe(card));
  }

  /* ------------------------------------------------------------------ */
  /* 5. #included is removed from the markup; drop any stale nav entry   */
  /* ------------------------------------------------------------------ */
  if (!q('#included')) qa('.section-links a[href="#included"]').forEach((a) => a.remove());

  /* ------------------------------------------------------------------ */
  /* 6. Shipping: segmented control + plain table per package            */
  /* ------------------------------------------------------------------ */
  const shippingSection = q('#shipping');
  const shippingPackages = (() => {
    const raw = Array.isArray(data.shipping) ? data.shipping : (data.shipping?.value || []);
    return raw.map((entry, index) => {
      let boxes = [];
      const rawBoxes = Array.isArray(entry.boxes) ? entry.boxes : entry.boxes?.value;
      if (Array.isArray(rawBoxes) && rawBoxes.length) {
        boxes = rawBoxes.map((box) => ({
          name: box.box_name ?? box.name ?? '',
          dimensions: box.dimensions ?? '',
          weight: box.weight ?? ''
        }));
      } else if (Array.isArray(entry.rows)) {
        const rows = entry.rows.filter((row) => Array.isArray(row) && row.length);
        const body = rows.length && /^box$/i.test(String(rows[0][0]).trim()) ? rows.slice(1) : rows;
        boxes = body.map((row) => ({ name: row[0] ?? '', dimensions: row[1] ?? '', weight: row[2] ?? '' }));
      }
      const note = noEmoji(entry.note || '');
      const countMatch = String(entry.box_count ?? note.match(/ships in\s+(\d+)\s+box/i)?.[1] ?? boxes.length);
      const shippingTime = entry.shipping_time
        ? noEmoji(entry.shipping_time)
        : (note.match(/(machine will be shipped[^.]*\.?)/i)?.[1] || '').trim();
      return {
        index,
        title: sentenceCase(entry.table_title || entry.title || `Package ${index + 1}`),
        boxCount: Number(countMatch) || boxes.length,
        shippingTime: shippingTime.replace(/\s*-\s*/g, '–').replace(/\.$/, ''),
        boxes
      };
    }).filter((entry) => entry.boxes.length);
  })();

  let selectShippingPackage = () => {};
  if (shippingSection && shippingPackages.length) {
    qa('.rf-box-explorer, .rf-shipping-data, .shipping-boxes, .shipping-panel', shippingSection).forEach((node) => node.remove());
    const block = el('div', 'rf-ship-tables', `
      <div class="rf-segmented" role="tablist" aria-label="Shipping package">${shippingPackages.map((pkg, i) => `
        <button type="button" class="rf-segment" role="tab" id="rf-ship-tab-${i}" aria-selected="${i === 0}" aria-controls="rf-ship-panel-${i}" tabindex="${i === 0 ? 0 : -1}">${esc(pkg.title)}</button>`).join('')}
      </div>
      ${shippingPackages.map((pkg, i) => `
      <div class="rf-ship-panel" role="tabpanel" id="rf-ship-panel-${i}" aria-labelledby="rf-ship-tab-${i}" tabindex="0"${i === 0 ? '' : ' hidden'}>
        <h3>${esc(pkg.title)}</h3>
        <p class="rf-ship-meta">Ships in ${pkg.boxCount} ${pkg.boxCount === 1 ? 'box' : 'boxes'}${pkg.shippingTime ? ` · ${esc(pkg.shippingTime)}` : ''}</p>
        <div class="rf-table-wrap">
          <table class="rf-ship-table">
            <thead>
              <tr><th scope="col">Box</th><th scope="col">Dimensions (L×W×H)</th><th scope="col">Weight</th></tr>
            </thead>
            <tbody>${pkg.boxes.map((box) => `
              <tr><td>${esc(sentenceCase(box.name))}</td><td>${esc(fixUnits(box.dimensions))}</td><td>${esc(fixUnits(box.weight))}</td></tr>`).join('')}
            </tbody>
          </table>
        </div>
      </div>`).join('')}`);
    const head = q('.shipping-head', shippingSection);
    const anchor = head || q('.section-heading', shippingSection);
    if (anchor) anchor.after(block); else q('.wrap', shippingSection)?.append(block);

    const tabs = qa('.rf-segment', block);
    const panels = qa('.rf-ship-panel', block);
    selectShippingPackage = (index, focus = false) => {
      const i = Math.max(0, Math.min(index, tabs.length - 1));
      tabs.forEach((tab, ti) => {
        const selected = ti === i;
        tab.setAttribute('aria-selected', String(selected));
        tab.tabIndex = selected ? 0 : -1;
      });
      panels.forEach((panel, pi) => { panel.hidden = pi !== i; });
      if (focus) tabs[i].focus();
    };
    tabs.forEach((tab, i) => {
      tab.addEventListener('click', () => selectShippingPackage(i));
      tab.addEventListener('keydown', (event) => {
        const keys = { ArrowRight: 1, ArrowLeft: -1, Home: -Infinity, End: Infinity };
        if (!(event.key in keys)) return;
        event.preventDefault();
        const delta = keys[event.key];
        const target = delta === -Infinity ? 0 : delta === Infinity ? tabs.length - 1 : (i + delta + tabs.length) % tabs.length;
        selectShippingPackage(target, true);
      });
    });
  }
  const shippingIndexFor = (packageValue) => {
    const digits = String(packageValue || '').match(/\d{3}/)?.[0];
    const index = digits
      ? shippingPackages.findIndex((pkg) => pkg.title.includes(digits))
      : shippingPackages.findIndex((pkg) => !/\d{3}\s*lb/i.test(pkg.title));
    return index >= 0 ? index : 0;
  };
  const syncShippingWithBuyBox = () => selectShippingPackage(shippingIndexFor(currentOptions()[2]));

  /* ------------------------------------------------------------------ */
  /* 7. Real setups + real experiences become snap carousels             */
  /* ------------------------------------------------------------------ */
  const setupsGrid = q('#real-setups .rf-setups-grid');
  if (setupsGrid) makeCarousel(setupsGrid, { heading: q('#real-setups .section-heading'), label: 'customer photos' });
  const experiencesGrid = q('#experiences .video-grid');
  if (experiencesGrid) makeCarousel(experiencesGrid, { heading: q('#experiences .section-heading'), label: 'customer videos' });

  /* ------------------------------------------------------------------ */
  /* 8. Technical details: every card in one grid, each with its image   */
  /* ------------------------------------------------------------------ */
  const detailGrid = q('#details .detail-grid');
  const detailMore = q('#details .rf-more-content');
  if (detailGrid && detailMore) {
    const moreGrid = q('.rf-more-grid', detailMore);
    if (moreGrid) [...moreGrid.children].forEach((card) => detailGrid.append(card));
    detailMore.remove();
  }
  if (detailGrid) {
    const items = Array.isArray(data.details) ? data.details : [];
    qa('.feature-card', detailGrid).forEach((card, index) => {
      let media = q('.feature-media', card);
      let img = media && q('img', media);
      if (img && img.getAttribute('src')) return;
      const item = items[index];
      const src = item?.image?.src || item?.image || '';
      if (!src || typeof src !== 'string') return;
      if (!media) {
        media = el('div', 'feature-media');
        card.prepend(media);
      }
      if (!img) {
        img = document.createElement('img');
        media.append(img);
      }
      img.src = src;
      img.alt = item.image?.alt || item.title || '';
      img.loading = 'lazy';
      img.decoding = 'async';
    });
  }

  /* ------------------------------------------------------------------ */
  /* Variant-dependent re-rendering                                      */
  /* ------------------------------------------------------------------ */
  // Registered after v2/v3's observers on the same node, so it runs after v2 has rebuilt
  // the package cards and can replace the delta with the absolute price.
  const priceNode = q('.purchase .price');
  const syncVariant = () => { renderPackagePrices(); syncShippingWithBuyBox(); };
  if (priceNode) new MutationObserver(syncVariant).observe(priceNode, { childList: true, characterData: true, subtree: true });
  syncVariant();

  document.documentElement.classList.add('rf-v4');
})();
