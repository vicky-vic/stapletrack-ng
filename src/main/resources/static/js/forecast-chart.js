/*
 * Draws a price chart with zero or more linear-trend projections:
 *   actual     — solid green line, amber points
 *   projection — dashed line joined to the last actual point, plus its 95% band
 *                (upper series filled down to the lower series with fill: '+1')
 * `chart` is a ForecastChart payload: { labels, actual, projections: [{ model, label, forecast, upper, lower }] }.
 * FULL projections are drawn in green, RECENT ones in amber with a tighter dash, so the two read apart.
 */
window.StapleCharts = (() => {
	const css = getComputedStyle(document.documentElement);
	const token = name => css.getPropertyValue(name).trim();
	const naira = new Intl.NumberFormat('en-NG', { style: 'currency', currency: 'NGN', maximumFractionDigits: 0 });

	const STYLES = {
		FULL: { rgb: '27, 67, 50', lineAlpha: 0.6, bandAlpha: 0.12, dash: [6, 5] },   // --st-green-700
		RECENT: { rgb: '233, 168, 58', lineAlpha: 1, bandAlpha: 0.2, dash: [3, 4] }  // --st-amber-500
	};

	function projectionDatasets(p) {
		const style = STYLES[p.model] || STYLES.FULL;
		const line = `rgba(${style.rgb}, ${style.lineAlpha})`;
		return [{
			label: p.label,
			data: p.forecast,
			borderColor: line,
			backgroundColor: line,
			borderDash: style.dash,
			borderWidth: 2,
			pointRadius: 3,
			pointBackgroundColor: '#FFFFFF',
			tension: 0,
			range: { upper: p.upper, lower: p.lower }
		}, {
			label: `${p.label} — 95% range`,
			data: p.upper,
			borderWidth: 0,
			pointRadius: 0,
			pointHitRadius: 0,
			backgroundColor: `rgba(${style.rgb}, ${style.bandAlpha})`,
			fill: { target: '+1' },
			tension: 0,
			isBand: true
		}, {
			label: `${p.label} — 95% range (lower)`,
			data: p.lower,
			borderWidth: 0,
			pointRadius: 0,
			pointHitRadius: 0,
			fill: false,
			tension: 0,
			isBand: true,
			hideInLegend: true
		}];
	}

	function render(canvas, chart, options = {}) {
		Chart.defaults.font.family = token('--font-sans');
		Chart.defaults.color = token('--st-ink-500');

		const datasets = [{
			label: 'Actual',
			data: chart.actual,
			borderColor: token('--st-green-700'),
			backgroundColor: token('--st-green-700'),
			pointBackgroundColor: token('--st-amber-500'),
			pointBorderColor: token('--st-green-700'),
			borderWidth: 2,
			pointRadius: options.pointRadius ?? 4,
			tension: 0,
			spanGaps: false
		}, ...chart.projections.flatMap(projectionDatasets)];

		return refitWhenFontsLoad(new Chart(canvas, {
			type: 'line',
			data: { labels: chart.labels, datasets },
			options: {
				maintainAspectRatio: false,
				interaction: { mode: 'index', intersect: false },
				plugins: {
					legend: {
						display: !!options.legend,
						labels: {
							// Draw swatches as 40px strokes that use each dataset's own dash pattern, instead of
							// Chart.js's default 20×10 box, which crops the dashes into a pixelated block.
							// With usePointStyle, the swatch width comes from pointStyleWidth (boxWidth alone is
							// capped at the font size) and a 'rect' chip's height from boxHeight.
							usePointStyle: true,
							pointStyle: 'line',
							boxWidth: 40,
							pointStyleWidth: 40,
							boxHeight: 10,
							filter: (item, data) => !data.datasets[item.datasetIndex].hideInLegend,
							generateLabels: chart => Chart.defaults.plugins.legend.labels.generateLabels(chart).map(label => {
								const ds = chart.data.datasets[label.datasetIndex];
								if (ds.isBand) {
									// Confidence bands have no border: show a flat colour chip of their fill
									label.pointStyle = 'rect';
									label.fillStyle = ds.backgroundColor;
									label.strokeStyle = 'transparent';
									label.lineWidth = 0;
									label.lineDash = [];
								}
								else {
									label.pointStyle = 'line';
									label.strokeStyle = ds.borderColor;
									label.fillStyle = 'transparent';
									label.lineWidth = ds.borderWidth || 2;
									label.lineDash = ds.borderDash || [];
								}
								return label;
							})
						}
					},
					tooltip: {
						filter: item => item.raw !== null && !item.dataset.isBand,
						callbacks: {
							label: ctx => {
								const i = ctx.dataIndex;
								const text = `${ctx.dataset.label}: ${naira.format(ctx.parsed.y)}`;
								// Projected points (not the join with the last actual) also show their range
								const range = ctx.dataset.range;
								if (range && chart.actual[i] === null) {
									return `${text} (${naira.format(range.lower[i])} – ${naira.format(range.upper[i])})`;
								}
								return text;
							}
						}
					}
				},
				scales: {
					x: { grid: { display: false } },
					y: { grid: { color: token('--st-border') }, ticks: { callback: value => naira.format(value) } }
				}
			}
		}));
	}

	/**
	 * Chart.js measures legend and tick text once, at creation, but re-measures on every redraw.
	 * If DM Sans finishes loading in between, the text grows, the last legend entry wraps onto a row
	 * the legend box never reserved, and it is clipped out of sight. Re-running layout once the page's
	 * fonts are ready keeps measurement and drawing in the same font.
	 */
	function refitWhenFontsLoad(chartInstance) {
		if (document.fonts && document.fonts.ready) {
			document.fonts.ready.then(() => chartInstance.update('none'));
		}
		return chartInstance;
	}

	return { render, refitWhenFontsLoad };
})();
