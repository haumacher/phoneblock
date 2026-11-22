document.addEventListener('DOMContentLoaded', () => {
    const chartDataElement = document.getElementById('dongle-platforms-data');
    const data = JSON.parse(chartDataElement.textContent);

    // Stack the two disjoint buckets: The bar height is the 90-day total.
    const scales = data.options.scales;
    scales.x = Object.assign({}, scales.x, { stacked: true });
    scales.y = Object.assign({}, scales.y, { stacked: true });

    new Chart(document.getElementById('dongle-platforms').getContext('2d'), data);
});
