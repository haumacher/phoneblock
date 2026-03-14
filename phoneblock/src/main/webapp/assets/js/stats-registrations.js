document.addEventListener('DOMContentLoaded', () => {
    const chartDataElement = document.getElementById('registrations-data');

	const data = JSON.parse(chartDataElement.textContent);

    // make chart
    new Chart(document.getElementById('registrations').getContext('2d'), data);
});
