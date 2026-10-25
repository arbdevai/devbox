document.addEventListener('DOMContentLoaded', () => {
  // 1. Mobile Navigation Drawer Toggle
  const mobileMenuBtn = document.getElementById('mobileMenuBtn');
  const mobileNavDrawer = document.getElementById('mobileNavDrawer');
  const mobileNavLinks = document.querySelectorAll('.mobile-nav-link');

  if (mobileMenuBtn && mobileNavDrawer) {
    mobileMenuBtn.addEventListener('click', () => {
      const isOpen = mobileNavDrawer.classList.toggle('open');
      mobileMenuBtn.classList.toggle('open', isOpen);
      mobileMenuBtn.setAttribute('aria-expanded', String(isOpen));
    });

    mobileNavLinks.forEach((link) => {
      link.addEventListener('click', () => {
        mobileNavDrawer.classList.remove('open');
        mobileMenuBtn.classList.remove('open');
        mobileMenuBtn.setAttribute('aria-expanded', 'false');
      });
    });
  }

  // 2. Interactive Phone Screen Switcher (Tabs + Prev/Next + Touch Swipe)
  const tabs = Array.from(document.querySelectorAll('.screen-tab'));
  const screenImg = document.getElementById('activeScreenImg');
  const screenCaption = document.getElementById('activeScreenCaption');
  const prevBtn = document.getElementById('prevScreenBtn');
  const nextBtn = document.getElementById('nextScreenBtn');
  const phoneTouchArea = document.getElementById('phoneTouchArea');
  let currentIndex = 0;

  function activateScreen(index) {
    if (!tabs.length) return;
    currentIndex = (index + tabs.length) % tabs.length;
    const activeTab = tabs[currentIndex];

    tabs.forEach((t) => t.classList.remove('active'));
    activeTab.classList.add('active');

    // Keep active pill visible inside horizontal tab strip on mobile
    activeTab.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });

    const newSrc = activeTab.getAttribute('data-img');
    const newCaption = activeTab.getAttribute('data-caption');

    if (screenImg && newSrc) {
      screenImg.style.opacity = '0.3';
      setTimeout(() => {
        screenImg.src = newSrc;
        screenImg.style.opacity = '1';
      }, 110);
    }

    if (screenCaption && newCaption) {
      screenCaption.textContent = newCaption;
    }
  }

  tabs.forEach((tab, idx) => {
    tab.addEventListener('click', () => activateScreen(idx));
  });

  if (prevBtn) {
    prevBtn.addEventListener('click', () => activateScreen(currentIndex - 1));
  }
  if (nextBtn) {
    nextBtn.addEventListener('click', () => activateScreen(currentIndex + 1));
  }

  // Touch swipe left/right on the phone mockup
  if (phoneTouchArea) {
    let startX = 0;
    phoneTouchArea.addEventListener('touchstart', (e) => {
      startX = e.changedTouches[0].screenX;
    }, { passive: true });

    phoneTouchArea.addEventListener('touchend', (e) => {
      const endX = e.changedTouches[0].screenX;
      const delta = endX - startX;
      if (Math.abs(delta) > 42) {
        if (delta < 0) activateScreen(currentIndex + 1);
        else activateScreen(currentIndex - 1);
      }
    }, { passive: true });
  }

  // 3. Copy Terminal Command Button
  const copyBtn = document.getElementById('copyCmdBtn');
  const cmdText = document.getElementById('cmdText');

  if (copyBtn && cmdText) {
    copyBtn.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(cmdText.textContent.trim());
        const original = copyBtn.textContent;
        copyBtn.textContent = 'Copied!';
        setTimeout(() => {
          copyBtn.textContent = original;
        }, 1800);
      } catch (_) {
        copyBtn.textContent = 'Copied';
      }
    });
  }

  // 4. Fullscreen Lightbox Zoom for Screenshots
  const lightboxModal = document.getElementById('lightboxModal');
  const lightboxImg = document.getElementById('lightboxImg');
  const lightboxClose = document.getElementById('lightboxClose');
  const zoomTriggers = document.querySelectorAll('[data-zoom]');

  function openLightbox(src) {
    if (!lightboxModal || !lightboxImg || !src) return;
    lightboxImg.src = src;
    lightboxModal.classList.add('open');
    lightboxModal.setAttribute('aria-hidden', 'false');
  }

  function closeLightbox() {
    if (!lightboxModal) return;
    lightboxModal.classList.remove('open');
    lightboxModal.setAttribute('aria-hidden', 'true');
  }

  zoomTriggers.forEach((el) => {
    el.addEventListener('click', () => openLightbox(el.getAttribute('data-zoom')));
  });

  if (screenImg) {
    screenImg.addEventListener('click', () => openLightbox(screenImg.getAttribute('src')));
  }

  if (lightboxClose) {
    lightboxClose.addEventListener('click', closeLightbox);
  }
  if (lightboxModal) {
    lightboxModal.addEventListener('click', (e) => {
      if (e.target === lightboxModal) closeLightbox();
    });
  }
});
