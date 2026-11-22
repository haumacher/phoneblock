document.addEventListener('DOMContentLoaded', () => {
    const chartDataElement = document.getElementById('dongle-platforms-data');
    const data = JSON.parse(chartDataElement.textContent);

    new Chart(document.getElementById('dongle-platforms').getContext('2d'), data);
});
