(() => {
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

  const stage = $('.gallery-stage');
  if (stage) {
    const gallery = stage.closest('.gallery');
    const thumbs = $('#thumbs');
    const pageData = JSON.parse($('#page-data')?.textContent || '{}');
    const model = pageData.media?.find((item) => item.media_type === 'model');
    const has3D = Boolean(model);
    const localConfigurator = ['localhost', '127.0.0.1'].includes(window.location.hostname);
    const configuratorUrl = localConfigurator
      ? 'http://127.0.0.1:5180/'
      : '/3d/index.html';
    const controls = document.createElement('div');
    controls.className = 'rf-view-controls';
    controls.innerHTML = `
      <div class="rf-view-toggle" role="group" aria-label="Product view">
        <button type="button" class="is-active" data-product-view="photo" aria-pressed="true">Photo</button>
        <button type="button" data-product-view="3d" aria-pressed="false"${has3D ? '' : ' disabled'}>3D</button>
      </div>
      <a class="rf-plan-space" href="${configuratorUrl}?model=m1&experience=immersive" target="_blank" rel="noopener">Plan your space <span aria-hidden="true">↗</span></a>`;

    const modelLayer = document.createElement('div');
    modelLayer.className = 'rf-model-layer';
    modelLayer.setAttribute('aria-hidden', 'true');
    modelLayer.innerHTML = '<span class="rf-model-loading" role="status">Loading 3D…</span>';
    stage.append(modelLayer, controls);

    let viewerReady = false;
    let configuratorFrame = null;
    const selectedValue = (selector, fallback) => $(selector)?.dataset.value || fallback;
    const productConfiguration = () => ({
      rack: selectedValue('[data-option="0"] .selected', 'Black'),
      bench: selectedValue('[data-option="1"] .selected', 'Black'),
      package: selectedValue('#packages .selected', 'No Plates'),
    });
    const syncConfigurator = () => configuratorFrame?.contentWindow?.postMessage({
      type: 'ritfit-product-config',
      configuration: productConfiguration(),
    }, '*');
    const ensureViewer = () => {
      if (viewerReady || !has3D) return;
      viewerReady = true;
      configuratorFrame = document.createElement('iframe');
      configuratorFrame.className = 'rf-configurator-frame';
      configuratorFrame.title = 'RitFit M1 PRO interactive 3D configurator';
      configuratorFrame.setAttribute('scrolling', 'no');
      configuratorFrame.src = `${configuratorUrl}?model=m1&embed=product`;
      configuratorFrame.setAttribute('allow', 'fullscreen; xr-spatial-tracking');
      configuratorFrame.addEventListener('load', () => {
        modelLayer.classList.add('is-loaded');
        syncConfigurator();
      }, { once: true });
      modelLayer.append(configuratorFrame);
    };

    const setProductView = (view) => {
      const is3D = view === '3d' && has3D;
      if (is3D) ensureViewer();
      if (is3D && gallery && !gallery.classList.contains('is-3d-mode')) {
        const thumbnailHeight = thumbs?.getBoundingClientRect().height || 64;
        const expansion = Math.min(window.innerWidth <= 680 ? 64 : 96, Math.max(48, thumbnailHeight));
        stage.style.setProperty('--rf-3d-expanded-height', `${Math.round(stage.getBoundingClientRect().height + expansion)}px`);
        stage.getBoundingClientRect();
      }
      gallery?.classList.toggle('is-3d-mode', is3D);
      stage.classList.toggle('is-3d-view', is3D);
      stage.dataset.productView = is3D ? '3d' : 'photo';
      modelLayer.setAttribute('aria-hidden', String(!is3D));
      if (thumbs) {
        thumbs.inert = is3D;
        thumbs.setAttribute('aria-hidden', String(is3D));
      }
      $$('[data-product-view]', controls).forEach((button) => {
        const selected = button.dataset.productView === (is3D ? '3d' : 'photo');
        button.classList.toggle('is-active', selected);
        button.setAttribute('aria-pressed', String(selected));
      });
      if (is3D) requestAnimationFrame(syncConfigurator);
    };
    controls.addEventListener('click', (event) => {
      const button = event.target.closest('[data-product-view]');
      if (button) setProductView(button.dataset.productView);
    });
    thumbs?.addEventListener('click', () => setProductView('photo'));
    $('.option-pair')?.addEventListener('click', () => requestAnimationFrame(syncConfigurator));
    $('#packages')?.addEventListener('click', () => requestAnimationFrame(syncConfigurator));
    window.addEventListener('message', (event) => {
      if (event.source === configuratorFrame?.contentWindow && event.data?.type === 'ritfit-3d-ready') syncConfigurator();
      if (event.source === configuratorFrame?.contentWindow && event.data?.type === 'ritfit-3d-configured') {
        stage.dataset.modelConfiguration = JSON.stringify(event.data.configuration);
      }
    });
  }

  const purchasePanel = $('.purchase');
  const configurationStart = $('.option-pair');
  if (stage && purchasePanel && configurationStart) {
    const stagePlaceholder = document.createElement('div');
    stagePlaceholder.className = 'rf-mobile-gallery-placeholder';
    stagePlaceholder.setAttribute('aria-hidden', 'true');
    stage.before(stagePlaceholder);

    const mobileQuery = window.matchMedia('(max-width: 680px)');
    let framePending = false;
    const updateStickyStage = () => {
      framePending = false;
      const isSticky = stage.classList.contains('is-config-sticky');
      if (!mobileQuery.matches) {
        stage.classList.remove('is-config-sticky', 'is-config-hidden');
        stagePlaceholder.classList.remove('is-active');
        stagePlaceholder.style.height = '';
        stage.style.removeProperty('--rf-mobile-stage-top');
        return;
      }

      const headerBottom = $('.header')?.getBoundingClientRect().bottom || 64;
      const stickyTop = Math.max(12, headerBottom + 12);
      const anchor = isSticky ? stagePlaceholder : stage;
      const anchorRect = anchor.getBoundingClientRect();
      const purchaseRect = purchasePanel.getBoundingClientRect();
      const shouldStick = anchorRect.top <= stickyTop && purchaseRect.bottom > stickyTop;

      if (shouldStick) {
        if (!isSticky) {
          stagePlaceholder.classList.add('is-active');
          stage.classList.add('is-config-sticky');
        }
        stagePlaceholder.style.height = `${stage.offsetHeight}px`;
        stage.style.setProperty('--rf-mobile-stage-top', `${stickyTop}px`);
        const stageHeight = stage.getBoundingClientRect().height;
        stage.classList.toggle('is-config-hidden', purchaseRect.bottom <= stickyTop + stageHeight);
      } else {
        stage.classList.remove('is-config-sticky', 'is-config-hidden');
        stagePlaceholder.classList.remove('is-active');
        stagePlaceholder.style.height = '';
      }
    };
    const requestStickyUpdate = () => {
      if (framePending) return;
      framePending = true;
      window.requestAnimationFrame(updateStickyStage);
    };

    window.addEventListener('scroll', requestStickyUpdate, { passive: true });
    window.addEventListener('resize', requestStickyUpdate);
    mobileQuery.addEventListener('change', requestStickyUpdate);
    new ResizeObserver(requestStickyUpdate).observe(stage);
    requestStickyUpdate();
  }

  const productLayout = $('.product-layout');
  const productTitle = $('.purchase h1');
  const productPrice = $('.purchase .price');
  if (productLayout && productTitle && productPrice) {
    const tabletSummary = document.createElement('div');
    tabletSummary.className = 'rf-tablet-summary';
    tabletSummary.innerHTML = `<div><span>A gym of your own</span><h1>${productTitle.textContent}</h1></div><div><b data-tablet-price>${productPrice.textContent}</b><a href="#rf-config">Configure yours</a></div>`;
    productLayout.prepend(tabletSummary);
    $('.purchase')?.setAttribute('id', 'rf-config');
    new MutationObserver(() => {
      const output = $('[data-tablet-price]');
      if (output) output.textContent = productPrice.textContent;
    }).observe(productPrice, { childList: true, subtree: true, characterData: true });
  }

  const packages = $('.packages');
  if (packages) {
    const clarity = document.createElement('section');
    clarity.className = 'rf-package-clarity';
    clarity.setAttribute('aria-live', 'polite');
    packages.after(clarity);

    const delivery = document.createElement('div');
    delivery.className = 'rf-decision-proof';
    delivery.innerHTML = `
      <span data-box-count>7 boxes</span>
      <span>Ships in 2–3 days</span>
      <span>Free shipping</span>
      <span>35-day returns</span>`;
    const trust = $('.trust-row');
    if (trust) trust.replaceWith(delivery);

    const updatePackage = () => {
      const selected = $('.package.selected', packages);
      const name = selected?.textContent.trim() || 'No plates';
      const has260 = /260/.test(name);
      const has370 = /370/.test(name);
      const plateCopy = has260
        ? '260 lb bumper plate set included'
        : has370
          ? '370 lb bumper plate set included'
          : 'Bumper plates are not included';
      const boxCount = has260 ? 10 : has370 ? 11 : 7;
      clarity.innerHTML = `
        <p>M1 PRO · 264 lb stack · BWB01 bench · ${plateCopy}</p>`;
      const count = $('[data-box-count]');
      if (count) count.textContent = `${boxCount} boxes`;
      const stickyConfiguration = $('.mobile-sticky small');
      if (stickyConfiguration) stickyConfiguration.textContent = `${options[0]} rack · ${name}`;
    };

    packages.addEventListener('click', () => requestAnimationFrame(updatePackage));
    $$('[data-option]').forEach((group) => group.addEventListener('click', () => requestAnimationFrame(updatePackage)));
    updatePackage();
  }

  const specsSection = $('#specs');
  const specGrid = $('#specs .spec-grid');
  if (specsSection && specGrid && !$('#specifications')) {
    const specifications = document.createElement('section');
    specifications.id = 'specifications';
    specifications.className = 'section';
    specifications.innerHTML = `
      <div class="wrap">
        <div class="section-heading">
          <div>
            <h2>Check every technical detail.</h2>
          </div>
        </div>
      </div>`;
    specifications.querySelector('.wrap').append(specGrid);
    specsSection.after(specifications);
  }

  const sectionHeadings = {
    included: 'Know exactly what comes home.',
    features: 'Cable, Smith, and pull-up training.',
    experiences: 'See it working in real homes.',
    details: 'Four details that support every rep.',
    specs: 'Measure the room before delivery.',
    specifications: 'Check every technical detail.',
    compare: 'Choose on the facts that matter.',
    shipping: 'Know what arrives and how.',
    reviews: 'Read what owners learned after setup.',
    faq: 'Resolve the last questions before buying.',
    setup: 'Add only what your training needs.'
  };
  Object.entries(sectionHeadings).forEach(([id, headingCopy]) => {
    const section = $(`#${id}`);
    const heading = section && $('.section-heading h2', section);
    if (heading) heading.textContent = headingCopy;
  });

  const navItems = [
    ['#product', 'Configurations'],
    ['#features', 'Features'],
    ['#specs', 'Gear Specs'],
    ['#included', 'Attachments'],
    ['#details', 'More Details'],
    ['#shipping', 'Shipping']
  ];
  $$('.section-links a').forEach((link, index) => {
    const item = navItems[index];
    if (!item) return;
    link.href = item[0];
    link.textContent = item[1];
  });

  $$('#experiences .video-card').slice(0, 3).forEach((card, index) => {
    const heading = $('h3', card);
    const meta = $('p', card);
    if (heading) heading.textContent = `M1 PRO owner story ${String(index + 1).padStart(2, '0')}`;
    if (meta) meta.textContent = 'Watch on YouTube';
  });

  const disclosureFor = (gridSelector, visibleCount, label) => {
    const grid = $(gridSelector);
    if (!grid) return;
    const items = [...grid.children];
    if (items.length <= visibleCount) return;
    const details = document.createElement('details');
    details.className = 'rf-more-content';
    const summary = document.createElement('summary');
    summary.textContent = label;
    const inner = document.createElement('div');
    inner.className = `${grid.className} rf-more-grid`;
    items.slice(visibleCount).forEach((item) => inner.append(item));
    details.append(summary, inner);
    grid.after(details);
  };

  disclosureFor('#features .feature-grid', 3, 'Explore three more training systems');
  disclosureFor('#details .detail-grid', 4, 'Explore four more engineering details');

  // Keep the document flow aligned with the product navigation. Supporting
  // sections stay beside the primary destination they expand on.
  const sectionOrder = ['features', 'experiences', 'specs', 'specifications', 'included', 'details', 'compare', 'shipping', 'reviews', 'faq', 'setup'];
  const main = $('main');
  const footer = $('main > .footer');
  sectionOrder.forEach((id) => {
    const section = $(`#${id}`);
    if (section && main) main.append(section);
  });
  if (footer && main) main.append(footer);

  $$('#specifications details.spec-group').forEach((detail, index) => {
    detail.open = index === 0;
  });

  const shippingSection = $('#shipping');
  const shippingPanels = shippingSection ? $$('.shipping-panel', shippingSection) : [];
  const shippingPackages = shippingSection ? $$('[data-shipping]', shippingSection) : [];
  if (shippingSection && shippingPanels.length && shippingPackages.length) {
    const numberFrom = (value) => Number(String(value).match(/[\d.]+/)?.[0] || 0);
    const packageData = shippingPanels.map((panel, packageIndex) => ({
      index: packageIndex,
      name: shippingPackages[packageIndex]?.childNodes[0]?.textContent.trim() || `Package ${packageIndex + 1}`,
      boxes: $$('tbody tr', panel).map((row, boxIndex) => {
        const cells = $$('td', row).map((cell) => cell.textContent.trim());
        const dimensions = (cells[1]?.match(/[\d.]+/g) || []).map(Number);
        return {
          index: boxIndex,
          name: cells[0] || `Box ${boxIndex + 1}`,
          length: dimensions[0] || 0,
          width: dimensions[1] || 0,
          height: dimensions[2] || 0,
          weight: numberFrom(cells[2]),
          dimensionsLabel: cells[1] || 'Dimensions unavailable',
          weightLabel: cells[2] || 'Weight unavailable'
        };
      })
    }));
    const explorer = document.createElement('div');
    explorer.className = 'rf-box-explorer';
    explorer.innerHTML = `
      <div class="rf-shipment-scene">
        <div class="rf-scene-main">
          <div class="rf-scene-data">
            <div class="rf-scene-stat rf-scene-group">
              <span>Current set</span>
              <strong data-rf-group-count></strong>
              <b data-rf-page-progress></b>
            </div>
            <div class="rf-scene-stat rf-scene-total">
              <span>Total shipment</span>
              <strong data-rf-total-weight></strong>
              <b><i data-rf-total-boxes></i> cartons</b>
            </div>
            <aside class="rf-scene-selected" aria-live="polite">
              <span>Selected carton</span>
              <h3 data-rf-box-name></h3>
              <strong data-rf-box-dimensions></strong>
              <b data-rf-box-weight></b>
            </aside>
          </div>
          <div class="rf-carton-stage" role="listbox" aria-label="Choose a carton to inspect"></div>
          <div class="rf-scene-controls">
            <div class="rf-scene-package-slot">
              <span class="rf-scene-control-label">Choose set</span>
            </div>
          </div>
        </div>
      </div>`;

    const dataDisclosure = document.createElement('details');
    dataDisclosure.className = 'rf-shipping-data';
    dataDisclosure.innerHTML = '<summary>View complete box data</summary><div class="rf-shipping-data-body"></div>';
    const dataBody = $('.rf-shipping-data-body', dataDisclosure);
    shippingPanels.forEach((panel) => dataBody.append(panel));
    const packageStrip = shippingPackages[0].parentElement;
    packageStrip.after(explorer, dataDisclosure);
    $('.rf-scene-package-slot', explorer).append(packageStrip);

    let activePackage = 0;
    let activeBox = 0;
    const formatInches = (value) => `${value.toFixed(value % 1 ? 2 : 0).replace(/0+$/, '').replace(/\.$/, '')}″`;
    const cartonMode = 'ai';
    const cartonSlug = (box) => {
      const name = box.name.toLowerCase();
      if (name.includes('weight stack a')) return 'weight-stack-a';
      if (name.includes('weight stack b')) return 'weight-stack-b';
      if (name.includes('weight stack c')) return 'weight-stack-c';
      if (/box [def]1/.test(name)) return 'small-parts';
      if (name.includes('bench')) return 'bench';
      if (name.includes('100')) return 'plates-100';
      if (name.includes('35')) return 'plates-35';
      if (name.includes('45')) return 'plates-45';
      if (name.includes('55')) return 'plates-55';
      return 'small-parts';
    };
    const cartonAsset = (box) => {
      const slug = cartonSlug(box);
      const asset = slug.startsWith('plates-') ? 'plates-unified' : slug;
      return `screenshots/cartons/${cartonMode}/${asset}.png`;
    };
    const cartonDisplayWidth = (box) => {
      if (cartonSlug(box).startsWith('plates-')) return 118;
      return Math.round(box.length < 16
        ? 145
        : Math.max(120, Math.min(350, 80 + box.length * 3.2)));
    };
    const cartonKey = (box) => box.name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
    explorer.dataset.cartonMode = cartonMode;
    const updateCartonButton = (button, box, index, slot) => {
      const accessibleLabel = `${box.name}, ${box.dimensionsLabel}, ${box.weightLabel}`;
      button.className = `rf-carton-item rf-slot-${slot}${index === activeBox ? ' selected' : ''}`;
      button.dataset.rfBox = index;
      button.dataset.cartonKey = cartonKey(box);
      button.setAttribute('aria-selected', index === activeBox);
      button.setAttribute('aria-label', accessibleLabel);
      button.style.setProperty('--rf-carton-w', `${cartonDisplayWidth(box)}px`);
      button.innerHTML = `
        <span class="rf-carton-number">${String(index + 1).padStart(2, '0')}</span>
        <span class="rf-carton-shape"><img src="${cartonAsset(box)}" alt="" draggable="false"></span>
        <span class="rf-carton-caption"><strong>${box.name}</strong><small>${box.dimensionsLabel}</small></span>`;
    };
    const createCartonButton = (box, index, slot) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.setAttribute('role', 'option');
      updateCartonButton(button, box, index, slot);
      button.addEventListener('click', () => renderBox(Number(button.dataset.rfBox)));
      return button;
    };
    const renderCartons = (animate = true) => {
      const data = packageData[activePackage];
      const pageBoxes = data.boxes;
      const stage = $('.rf-carton-stage', explorer);
      const stageRect = stage.getBoundingClientRect();
      const currentButtons = $$('[data-carton-key]:not(.is-exiting)', stage);
      const currentByKey = new Map(currentButtons.map((button) => [button.dataset.cartonKey, button]));
      const firstRects = new Map(currentButtons.map((button) => [button.dataset.cartonKey, button.getBoundingClientRect()]));
      const nextButtons = pageBoxes.map((box, pageBoxIndex) => {
        const index = pageBoxIndex;
        const key = cartonKey(box);
        const existing = currentByKey.get(key);
        const button = existing || createCartonButton(box, index, pageBoxIndex + 1);
        updateCartonButton(button, box, index, pageBoxIndex + 1);
        if (!existing && animate) button.classList.add('is-entering');
        currentByKey.delete(key);
        return button;
      });
      currentByKey.forEach((button) => {
        const rect = firstRects.get(button.dataset.cartonKey);
        button.className = 'rf-carton-item is-exiting';
        button.style.left = `${rect.left - stageRect.left + rect.width / 2}px`;
        button.style.top = `${rect.top - stageRect.top + rect.height / 2}px`;
        button.style.width = `${rect.width}px`;
        window.setTimeout(() => button.remove(), animate ? 420 : 0);
      });
      nextButtons.forEach((button) => stage.append(button));
      if (animate) {
        nextButtons.forEach((button) => {
          const first = firstRects.get(button.dataset.cartonKey);
          if (!first) return;
          const last = button.getBoundingClientRect();
          button.style.setProperty('--rf-flip-x', `${first.left - last.left}px`);
          button.style.setProperty('--rf-flip-y', `${first.top - last.top}px`);
          button.style.setProperty('--rf-flip-scale', `${Math.max(.72, Math.min(1.28, first.width / Math.max(last.width, 1)))}`);
          button.classList.add('is-flipping');
        });
        requestAnimationFrame(() => requestAnimationFrame(() => {
          nextButtons.forEach((button) => {
            button.style.setProperty('--rf-flip-x', '0px');
            button.style.setProperty('--rf-flip-y', '0px');
            button.style.setProperty('--rf-flip-scale', '1');
            button.classList.remove('is-entering');
          });
        }));
      }
      $('[data-rf-page-progress]', explorer).textContent = `${String(data.boxes.length).padStart(2, '0')} cartons`;
      $('[data-rf-group-count]', explorer).textContent = data.name;
    };
    const renderBox = (boxIndex, animate = true) => {
      const boxes = packageData[activePackage].boxes;
      activeBox = Math.max(0, Math.min(boxIndex, boxes.length - 1));
      const box = boxes[activeBox];
      if (!box) return;
      $$('[data-rf-box]', explorer).forEach((button) => {
        const selected = Number(button.dataset.rfBox) === activeBox;
        button.classList.toggle('selected', selected);
        button.setAttribute('aria-selected', selected);
        button.tabIndex = selected ? 0 : -1;
      });
      $('[data-rf-box-name]', explorer).textContent = box.name;
      $('[data-rf-box-weight]', explorer).textContent = box.weightLabel;
      $('[data-rf-box-dimensions]', explorer).textContent = box.dimensionsLabel;
    };
    const renderPackage = (packageIndex, animate = true) => {
      activePackage = Math.max(0, Math.min(packageIndex, packageData.length - 1));
      const data = packageData[activePackage];
      explorer.dataset.cartonCount = String(data.boxes.length);
      activeBox = 0;
      const totalWeight = data.boxes.reduce((sum, box) => sum + box.weight, 0);
      $('[data-rf-total-boxes]', explorer).textContent = String(data.boxes.length).padStart(2, '0');
      $('[data-rf-total-weight]', explorer).textContent = `${totalWeight.toFixed(1)} lbs`;
      shippingPanels.forEach((panel, index) => { panel.hidden = index !== activePackage; });
      renderCartons(animate);
      renderBox(0, false);
    };

    shippingPackages.forEach((button, index) => {
      button.onclick = () => {
        shippingPackages.forEach((item, itemIndex) => {
          const selected = itemIndex === index;
          item.classList.toggle('selected', selected);
          item.setAttribute('aria-pressed', selected);
        });
        renderPackage(index, true);
      };
    });
    renderPackage(0, false);
  }

  document.documentElement.classList.add('rf-enhanced');
})();
