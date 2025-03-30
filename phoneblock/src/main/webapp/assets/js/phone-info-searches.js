document.addEventListener('DOMContentLoaded', () => {
    const chartDataElement = document.getElementById('searches-data');

	const data = JSON.parse(chartDataElement.textContent);

    // make chart
    new Chart(document.getElementById('searches').getContext('2d'), data);
});