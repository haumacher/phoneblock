document.addEventListener('DOMContentLoaded', () => {
    const chartDataElement = document.getElementById('blocked-by-country-data');

	const data = JSON.parse(chartDataElement.textContent);

    // make chart
    new Chart(document.getElementById('blocked-by-country').getContext('2d'), data);
});
