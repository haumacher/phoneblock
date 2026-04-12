document.addEventListener('DOMContentLoaded', () => {
    const chartDataElement = document.getElementById('installations-data');

	const data = JSON.parse(chartDataElement.textContent);

    data.options = data.options || {};
    data.options.plugins = data.options.plugins || {};
    data.options.plugins.tooltip = data.options.plugins.tooltip || {};
    data.options.plugins.tooltip.callbacks = data.options.plugins.tooltip.callbacks || {};
    data.options.plugins.tooltip.callbacks.label = function(ctx) {
        const ds = ctx.dataset.data;
        const i = ctx.dataIndex;
        const value = ds[i];
        const prev = i > 0 ? ds[i - 1] : null;
        let text = ctx.dataset.label + ': ' + value;
        if (prev !== null) {
            const diff = value - prev;
            const sign = diff >= 0 ? '+' : '';
            text += ' (' + sign + diff + ')';
        }
        return text;
    };

    // make chart
    new Chart(document.getElementById('installations').getContext('2d'), data);
});
