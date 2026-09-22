/* RitFit M1 PRO product page — v5 refinements.
   Loads AFTER the inline page script, ritfit-product-enhancements.js and ritfit-v2/v3/v4.js
   and only adds behaviour on top of them. Reuses the page globals (D, money, escapeHtml,
   options, variant, stickyObserver). Nothing in the earlier files is modified. */
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
  const mobile = window.matchMedia('(max-width: 680px)');
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const currentOptions = () => (typeof options !== 'undefined' && Array.isArray(options) ? options : ['Black', 'Black', 'No Plates']);
  const throttle = (fn, wait = 80) => {
    let last = 0;
    let timer = 0;
    return () => {
      const now = Date.now();
      if (now - last >= wait) { last = now; fn(); return; }
      if (timer) return;
      timer = window.setTimeout(() => { timer = 0; last = Date.now(); fn(); }, wait - (now - last));
    };
  };
  const CHEVRON_LEFT = '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m15 5-7 7 7 7"/></svg>';
  const CHEVRON_RIGHT = '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m9 5 7 7-7 7"/></svg>';
  const PLAY_ICON = '<svg class="rf-icon-play" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M8 5.5v13l10.5-6.5z"/></svg>';
  const PAUSE_ICON = '<svg class="rf-icon-pause" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M7 5h3.6v14H7zM13.4 5H17v14h-3.4z"/></svg>';

  /* ------------------------------------------------------------------ */
  /* Shared: snap carousel with round prev/next buttons (same as v4)     */
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
    if (heading) { heading.classList.add('rf-has-carousel'); heading.append(nav); } else scroller.before(nav);
    const step = () => {
      const first = scroller.firstElementChild;
      const second = first?.nextElementSibling;
      return first && second ? second.getBoundingClientRect().left - first.getBoundingClientRect().left : scroller.clientWidth;
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
    return { update };
  };

  /* ------------------------------------------------------------------ */
  /* 1. Feature videos: manual only on desktop, ≥60 % autoplay on mobile */
  /* ------------------------------------------------------------------ */
  // v4 owns its own observer and buttons on the current video elements. v5 swaps in a
  // fresh <video> and button per card so it is the only script driving playback; the
  // detached v4 elements get a no-op play() so v4's observer cannot start a download.
  const safePlay = (video) => { const p = video.play(); if (p && p.catch) p.catch(() => {}); };
  const videoCards = qa('#features .feature-card').filter((card) => q('video', card));
  const videoState = new Map();
  videoCards.forEach((card) => {
    const media = q('.feature-media', card);
    const oldVideo = q('video', card);
    if (!media || !oldVideo) return;
    const video = oldVideo.cloneNode(true);
    oldVideo.replaceWith(video);
    Object.defineProperty(oldVideo, 'play', { value: () => Promise.resolve(), configurable: true });
    qa('.rf-video-toggle', media).forEach((node) => node.remove());
    video.removeAttribute('controls');
    video.controls = false;
    video.muted = true;
    video.defaultMuted = true;
    video.loop = true;
    video.playsInline = true;
    video.setAttribute('muted', '');
    video.setAttribute('loop', '');
    video.setAttribute('playsinline', '');
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
    ['play', 'playing', 'pause', 'ended'].forEach((type) => video.addEventListener(type, syncButton));
    button.addEventListener('click', () => {
      if (video.paused) { state.manual = 'play'; safePlay(video); } else { state.manual = 'pause'; video.pause(); }
      syncButton();
    });
  });
  if (videoCards.length && 'IntersectionObserver' in window) {
    const videoObserver = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        const state = videoState.get(entry.target);
        if (!state) return;
        if (!entry.isIntersecting) {
          state.manual = null;
          if (!state.video.paused) state.video.pause();
          return;
        }
        // Desktop (>680px): never autoplay; the button is the only way to start a video.
        if (!mobile.matches) return;
        if (entry.intersectionRatio >= 0.6) {
          if (state.manual !== 'pause' && !reducedMotion.matches && state.video.paused) safePlay(state.video);
        } else if (state.manual !== 'play' && !state.video.paused) {
          state.video.pause();
        }
      });
    }, { threshold: [0, 0.6] });
    videoCards.forEach((card) => videoObserver.observe(card));
    // Leaving the mobile breakpoint pauses anything that was auto-playing.
    mobile.addEventListener('change', () => {
      if (mobile.matches) return;
      videoState.forEach((state) => { if (state.manual !== 'play' && !state.video.paused) state.video.pause(); });
    });
  }

  /* ------------------------------------------------------------------ */
  /* 2. Package cards: struck compare-at beside the absolute price       */
  /* ------------------------------------------------------------------ */
  const packageVariant = (value) => {
    const [rack, bench] = currentOptions();
    return (data.variants || []).find((v) => v.options?.[0] === rack && v.options?.[1] === bench && v.options?.[2] === value);
  };
  const renderPackagePrices = () => {
    qa('#packages .package').forEach((button) => {
      const v = packageVariant(button.dataset.value);
      const output = q('.rf-package-diff', button);
      if (!output || !v) return;
      output.classList.add('rf-package-price');
      const hasCompare = typeof v.compare_at_price === 'number' && v.compare_at_price > v.price;
      const html = `${hasCompare ? `<del><span class="rf-sr-only">Was </span>${esc(fmt(v.compare_at_price))}</del>` : ''}<b>${esc(fmt(v.price))}</b>`;
      if (output.innerHTML !== html) output.innerHTML = html;
    });
  };

  /* ------------------------------------------------------------------ */
  /* 3. Sticky add-to-cart bar: visible by default on mobile             */
  /* ------------------------------------------------------------------ */
  // Confirm the inline observer is off (v4 disconnected it; disconnect() is idempotent).
  if (typeof stickyObserver !== 'undefined' && stickyObserver && typeof stickyObserver.disconnect === 'function') {
    stickyObserver.disconnect();
    const pending = typeof stickyObserver.takeRecords === 'function' ? stickyObserver.takeRecords().length : 0;
    console.info(`[ritfit-v5] inline stickyObserver disconnected (pending records: ${pending})`);
  }
  // Fresh bar element: every earlier reference (inline stickyBuy, v2's label observer,
  // v4's observer) now points at a detached node and can no longer toggle this one.
  const oldBar = q('.mobile-sticky');
  const stickyBar = oldBar ? el('div', 'mobile-sticky rf-sticky-v5') : null;
  if (oldBar && stickyBar) {
    [...oldBar.childNodes].forEach((node) => stickyBar.append(node));
    oldBar.replaceWith(stickyBar);
    stickyBar.removeAttribute('hidden');
  }
  const inlinePanel = q('.purchase .rf-atc') || q('.buy-row') || q('#add-cart');
  const packagesNode = q('#packages');
  const visibility = { panel: false, packages: false };
  const applySticky = () => {
    if (!stickyBar) return;
    if (mobile.matches) {
      // Mobile path: class only; hidden while the inline panel intersects the viewport.
      if (stickyBar.hasAttribute('hidden')) stickyBar.removeAttribute('hidden');
      stickyBar.classList.toggle('rf-sticky-off', visibility.panel);
    } else {
      // Desktop path: original rule, [hidden] attribute.
      stickyBar.classList.remove('rf-sticky-off');
      const show = !visibility.panel && !visibility.packages;
      if (stickyBar.hidden === show) stickyBar.hidden = !show;
    }
  };
  const measure = () => {
    if (!inlinePanel) return;
    const rect = inlinePanel.getBoundingClientRect();
    const vh = window.innerHeight || document.documentElement.clientHeight;
    visibility.panel = rect.height > 0 && rect.bottom > 0 && rect.top < vh;
    if (packagesNode) {
      const p = packagesNode.getBoundingClientRect();
      const visibleHeight = Math.min(p.bottom, vh) - Math.max(p.top, 0);
      visibility.packages = p.height > 0 && visibleHeight / p.height >= 0.5;
    }
    applySticky();
  };
  if (stickyBar && inlinePanel) {
    if ('IntersectionObserver' in window) {
      const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          if (entry.target === inlinePanel) visibility.panel = entry.isIntersecting;
          else if (entry.target === packagesNode) visibility.packages = entry.isIntersecting && entry.intersectionRatio >= 0.5;
        });
        applySticky();
      }, { threshold: [0, 0.5] });
      observer.observe(inlinePanel);
      if (packagesNode) observer.observe(packagesNode);
    }
    const scheduleMeasure = throttle(measure);
    ['scroll', 'resize', 'orientationchange', 'pageshow', 'load'].forEach((type) => window.addEventListener(type, scheduleMeasure, { passive: true }));
    document.addEventListener('visibilitychange', scheduleMeasure);
    mobile.addEventListener('change', measure);
    // Guard against anything re-adding [hidden] on mobile.
    new MutationObserver(() => { if (mobile.matches && stickyBar.hasAttribute('hidden')) stickyBar.removeAttribute('hidden'); })
      .observe(stickyBar, { attributes: true, attributeFilter: ['hidden'] });
    measure();
    [300, 1000, 2500].forEach((delay) => window.setTimeout(measure, delay));
  }

  /* ------------------------------------------------------------------ */
  /* 4. Gear specs: diagram column beside the spec groups                */
  /* ------------------------------------------------------------------ */
  const specGrid = q('#specifications .spec-grid');
  const specsImage = data.specs_image && (typeof data.specs_image === 'string' ? { src: data.specs_image } : data.specs_image);
  if (specGrid && specsImage && specsImage.src && !q('#specifications .rf-specs-layout')) {
    const layout = el('div', 'rf-specs-layout');
    const figure = el('figure', 'rf-specs-figure', `<img src="${esc(specsImage.src)}" alt="${esc(specsImage.alt || 'M1 PRO gear specs diagram')}" loading="lazy" decoding="async">`);
    specGrid.before(layout);
    layout.append(figure, specGrid);
  }

  /* ------------------------------------------------------------------ */
  /* 5. Technical details become a snap carousel                         */
  /* ------------------------------------------------------------------ */
  const detailGrid = q('#details .detail-grid');
  if (detailGrid) {
    // v4 already flattened the disclosure; make sure nothing is left inside one.
    const more = q('#details .rf-more-grid');
    if (more) [...more.children].forEach((card) => detailGrid.append(card));
    q('#details .rf-more-content')?.remove();
    makeCarousel(detailGrid, { heading: q('#details .section-heading'), label: 'technical details' });
  }

  /* ------------------------------------------------------------------ */
  /* Variant-dependent re-rendering (runs after v2/v3/v4 observers)      */
  /* ------------------------------------------------------------------ */
  const priceNode = q('.purchase .price');
  if (priceNode) new MutationObserver(renderPackagePrices).observe(priceNode, { childList: true, characterData: true, subtree: true });
  renderPackagePrices();

  document.documentElement.classList.add('rf-v5');
})();
