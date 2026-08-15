const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  await page.setViewportSize({ width: 1920, height: 1080 });

  await page.goto('http://8.146.228.64:3000', { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(2000);

  await page.click('button:has-text("安全态势")');
  await page.waitForTimeout(5000);

  // Globe at x:1284, y:819, w:612, h:440
  // Attack lines extend from globe to left side of page
  // Capture from left side of globe area to include all lines
  const screenshotPath = 'd:/ASUS/Documents/jiedan/waibao/3D地球_仅地球.png';
  await page.screenshot({
    path: screenshotPath,
    clip: {
      x: 700,    // Start from left side to include attack lines
      y: 760,    // Top of globe area
      width: 1220, // To right edge of viewport
      height: 500
    },
    type: 'png'
  });

  console.log('Screenshot saved to: ' + screenshotPath);

  await browser.close();
  process.exit(0);
})().catch(e => {
  console.error('Error:', e.message);
  process.exit(1);
});
