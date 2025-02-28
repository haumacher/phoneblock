document.addEventListener('DOMContentLoaded', () => {
    const chartDataElement = document.getElementById('searches-data');

    // get data
    const labels = chartDataElement.getAttribute('searches-labels').split(",");
    const dataset = chartDataElement.getAttribute('searches-dataset').split(",");

    // make chart
    new Chart(document.getElementById('searches').getContext('2d'), {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: 'Suchanfragen in der letzten Woche',
                data: dataset,
                fill: false,
                borderColor: 'rgb(75, 192, 192)',
                tension: 0.1
            }]
        },
        options: {
            scales: {
                y: {
                    beginAtZero: true
                }
            }
        }
    });
});