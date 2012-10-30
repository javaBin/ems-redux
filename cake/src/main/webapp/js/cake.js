var cake = {};
//cake.templates = {};
cake.date = {};

cake.date.parse = function(dateString) {
    return Date.parseExact(dateString, 'yyyy-MM-ddTHH:mm:ssZ');
}

cake.date.toString = function(date) {
    return date.toString('yyyy-MM-ddTHH:mm:ssZ');
}

cake.loadRoot = function() {
    var root = $('head link[rel="nofollow ems"]').attr("href");
    console.log("configured root is: " + root);

    $.ajax({"url": root, dataType: "json"}).success(function(data) {
        cake.root = data;
        //cake.templates.event = _.template($('#event-template').html());
        var eventHref = fromObject(cake.root).findLinkByRel("event collection").href;
        console.log(eventHref);
        var x = new AppView({url: eventHref});
    });
}

cake.loadTemplate = function(href, name) {
    $.ajax({"url": href, dataType: "html"}).success(function(temp) {
        cake.templates[name] = temp;
    });
}

$(document).ready(function() {
    $.ajaxSetup({
        crossDomain: true,
        dataType: "json"
    });
    cake.loadRoot();

    $('#events').click(function() {
        var href = fromObject(cake.root).findLinkByRel("event collection").href;
        console.log(href);
        cake.events(href);
    });

    $("#mainContent").click(function(event) {
        var target = $(event.target);
        if (target.is('a')) {
            if (target.attr("rel") == 'event item') {
                cake.sessions(target.text(), target.attr("data-session-url"));
            }
            if (target.attr("rel") == "session item") {
                cake.session(target.attr("data-url"));
            }
        }
    });
});