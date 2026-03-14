document.addEventListener('DOMContentLoaded', () => {
    const chartDataElement = document.getElementById('installations-data');

	const data = JSON.parse(chartDataElement.textContent);

    // make chart
    new Chart(document.getElementById('installations').getContext('2d'), data);
});
