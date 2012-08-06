var cake = {};
cake.templates = {};
cake.date = {};

cake.get = function(href, callback) {
    $.ajax({url: "/ajax?href=" + href}).success(callback);
}

cake.date.parse = function(dateString) {
    return Date.parseExact(dateString, 'yyyy-MM-ddTHH:mm:ssZ');
}

cake.date.toString = function(date) {
    return date.toString('yyyy-MM-ddTHH:mm:ssZ');
}

cake.loadRoot = function() {
    var root = $('head link[rel="nofollow ems"]').attr("href");
    console.log("configured root is: " + root);

    cake.get(root, function(data) {
        cake.root = data;
    });
}

cake.loadTemplate = function(href, name) {
    $.ajax({"url": href, dataType: "html"}).success(function(temp) {
        cake.templates[name] = temp;
    });
}

cake.events = function(href) {
    cake.get(href, function(data) {
        var events = _.map(fromObject(data).collection.items, function(item) {
            var i = item.toObject();
            i.sessionHref = item.findLinkByRel("session collection").href;
            i.href = item.href;
            return i;
        });
        var rendered = Mustache.render(cake.templates.events, {events: events});
        $('#mainContent').html(rendered);
    });
}

cake.sessions = function(title, href) {
    cake.get(href, function(data) {
        var sessions = _.map(fromObject(data).collection.items, function(item) {
            var s = item.toObject();
            s.href = item.href;
            return s;
        });
        var rendered = Mustache.render(cake.templates.event, {title: title, sessions: sessions});
        $("#mainContent").html(rendered);
    });
}

cake.session = function(href) {
    cake.get(href, function(data) {
        var session = _.map(fromObject(data).collection.items, function(item) {
            var s = item.toObject();
            console.log(s)
            return s;
        });
        var rendered = Mustache.render(cake.templates.session, {session: _.head(session)});
        $("#mainContent").html(rendered);
    });
}

$(document).ready(function() {
    $.ajaxSetup({
        crossDomain: true,
        dataType: "json"
    });
    cake.loadTemplate("/templates/events.html", "events");
    cake.loadTemplate("/templates/event.html", "event");
    cake.loadTemplate("/templates/session.html", "session");

    cake.loadRoot();

    $('#events').click(function() {
        cake.events(fromObject(cake.root).findLinkByRel("event collection").href);
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