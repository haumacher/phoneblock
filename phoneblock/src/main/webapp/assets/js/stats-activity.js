document.addEventListener('DOMContentLoaded', () => {
    const chartDataElement = document.getElementById('activity-data');

    const data = JSON.parse(chartDataElement.textContent);

    // make chart
    new Chart(document.getElementById('activity').getContext('2d'), data);
});
