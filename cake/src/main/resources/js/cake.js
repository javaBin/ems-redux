var cake = {};

cake.get = function(href, callback) {
    $.ajax({url: "/ajax?href=" + href}).success(callback);
}

cake.events = function(href) {
    cake.get(href, function(data) {
        console.log(data);
        $('#mainContent').html("<h2>Hello</h2>" + JSON.stringify(data));
    });
}

$(document).ready(function() {
    $.ajaxSetup({
        crossDomain: true,
        dataType: "json"
    });
    $('#events').click(function() {
        console.log("Klikker på ting");
        cake.events("http://localhost:8081/events");
    });
});