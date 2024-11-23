document.addEventListener('DOMContentLoaded', () => {
    const chartDataElement = document.getElementById('ratings-data');

    // get data
    const labels = chartDataElement.getAttribute('ratings-labels').split(",");
    const dataset = chartDataElement.getAttribute('ratings-dataset').split(",");
    const backgroundColor = chartDataElement.getAttribute('ratings-backgroundColor').split("|");
    const borderColor = chartDataElement.getAttribute('ratings-borderColor').split("|");

    // make chart
    new Chart(document.getElementById('ratings').getContext('2d'), {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: 'Anzahl Bewertungen',
                data: dataset,
                backgroundColor: backgroundColor,
                borderColor: borderColor,
                borderWidth: 1
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