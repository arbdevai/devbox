document.addEventListener('DOMContentLoaded', () => {
  const tabs = document.querySelectorAll('.screen-tab');
  const screenImg = document.getElementById('activeScreenImg');
  const screenCaption = document.getElementById('activeScreenCaption');

  tabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      tabs.forEach((t) => t.classList.remove('active'));
      tab.classList.add('active');

      const newSrc = tab.getAttribute('data-img');
      const newCaption = tab.getAttribute('data-caption');

      if (screenImg && newSrc) {
        screenImg.style.opacity = '0.35';
        setTimeout(() => {
          screenImg.src = newSrc;
          screenImg.style.opacity = '1';
        }, 120);
      }

      if (screenCaption && newCaption) {
        screenCaption.textContent = newCaption;
      }
    });
  });

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
});
