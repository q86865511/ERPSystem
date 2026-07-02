// Set the color scheme before first paint (Mantine's localStorage key) to avoid a light/dark flash.
// External file (not inline) so the nginx CSP can stay `script-src 'self'` without a hash/nonce.
(function () {
  try {
    var s = localStorage.getItem('mantine-color-scheme-value');
    var dark = s === 'dark' || (s !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
    document.documentElement.setAttribute('data-mantine-color-scheme', dark ? 'dark' : 'light');
  } catch (e) {}
})();
