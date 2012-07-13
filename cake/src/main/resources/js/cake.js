var cake = {};

cake.events = function(href) {
    $.ajax({url: "/ajax?href=" + href}).success(function(data) {
        console.log("We were successful");
        $('.container').html("<h2>Hello</h2>" + data);
    });
}

$(document).ready(function() {
    $.ajaxSetup({
        //accepts: {Accept: "application/vnd.collection+json"},
        crossDomain: true,
        dataType: "json"
    });
    $('#events').click(function(){
        console.log("Klikker på ting");
        cake.events("http://localhost:8081/events");
    });
});