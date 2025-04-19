document.addEventListener('DOMContentLoaded', () => {
    const chartDataElement = document.getElementById('ratings-data');

	const data = JSON.parse(chartDataElement.textContent);

    // make chart
    new Chart(document.getElementById('ratings').getContext('2d'), data);
});