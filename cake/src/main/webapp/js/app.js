var app = {};
app.date = {};

angular.module('app', []).
    config(function($routeProvider) {
        $routeProvider.
            when('/', {controller:app.Main, templateUrl: 'about.html'}).
            when('/events/:name', {controller:app.SessionList, templateUrl:'sessions.html'}).
            when('/events/:eventName/session/:sessionTitle', {controller:app.SingleSession, templateUrl:'single-session.html'})./*.
            when('/edit/:projectId', {controller:EditCtrl, templateUrl:'detail.html'}).
            when('/new', {controller:CreateCtrl, templateUrl:'detail.html'}).*/
            otherwise({redirectTo:'/'});
    }).run(function($http) {
        app.loadRoot($http, function(root) {
            console.log("The configured root is " + root)
        });
    });

app.date.parse = function(dateString) {
    return Date.parseExact(dateString, 'yyyy-MM-ddTHH:mm:ssZ');
}

app.date.toString = function(date) {
    return date.toString('yyyy-MM-ddTHH:mm:ssZ');
}

app.loadRoot = function($http, cb) {
    if (!app.root) {
        var root = $('head link[rel="nofollow index"]').attr("href");
        console.log("The configured root is: " + root)

        $http.get(root).success(function(data) {
            app.root = fromObject(data);
            cb(app.root);
        });
    }
    else {
        cb(app.root);
    }
}

app.parseItem = function(item) {
    var i = {};
    i.data = item.toObject();
    i.href = item.href;
    i.links = item.links;
    return i;
}

app.LoadEvents = function($scope, $http) {
    if (!$scope.events) {
        app.loadRoot($http, function(root) {
            var eventHref = root.findLinkByRel("event collection").href;
            console.log(eventHref);
            $http.get(eventHref).success(function(data) {
                var events = _.map(fromObject(data).collection.items, app.mapEvent);
                app.events = events;
                $scope.events = events;
            });
        });
    }
}

app.Main = function($scope, $http) {
    console.log("Main called" + $scope);
}

app.SessionList = function($scope, $routeParams, $http) {
    app.loadRoot($http, function(root) {
        var query = root.findQueryByRel("event by-name");
        $http.get(query.expand({"name": $routeParams.name})).success(function(eventCollection){
            var event = app.mapEvent(_.head(fromObject(eventCollection).collection.items));
            $http.get(event.sessions).success(function(data) {
                var sessions = _.map(fromObject(data).collection.items, app.mapSession);
                $scope.sessions = sessions;
                $scope.name = event.data.name;
            });
        });
    });
}

app.SingleSession = function($scope) {

}

app.mapEvent = function(item) {
    var i = app.parseItem(item)
    i.data.start = app.date.parse(item.data.start);
    i.data.end = app.date.parse(item.data.end);
    i.sessions = item.findLinkByRel("session collection").href;
    return i;
}

app.mapSession = function(item) {
    var i = app.parseItem(item)
    i.keywordsAsString = toCSV(i.data.keywords);
    i.tagsAsString = toCSV(i.data.tags);
    i.state = app.mapState(i.data.state);
    i.format = app.mapFormat(i.data.format);
    i.level = app.mapLevel(i.data.level);
    i.speakers = findLinksByRel(i, "speaker item");
    console.log(i.speakers);
    i.speakersAsString = toCSV(i.speakers.map(function(i){return i.prompt}));
    return i;
}

app.mapState = function(state) {
    var object = {
        name: state
    }

    switch(state) {
        case "approved":
            object.icon = "icon-thumbs-up";
            break;
        case "rejected":
            object.icon = "icon-thumbs-down";
            break;
        case "pending":
        default:
            object.icon = "icon-repeat";
    }

    return object;
}

app.mapFormat = function(form) {
    var object = {
        name: form
    }
    console.log(form);
    switch(form) {
        case "lightning-talk":
            object.icon = "icon-bolt";
            break;
        case "panel":
            object.icon = "icon-group";
            break;
        case "bof":
            object.icon = "icon-comments";
            break;
        case "presentation":
        default:
            object.icon = "icon-comment-alt";
    }

    return object;
}

app.mapLevel = function(level) {
    var object = {
        name: level
    }

    switch (level) {
        case "beginner":
            object.icons = ["icon-star", "icon-star-empty", "icon-star-empty", "icon-star-empty", "icon-star-empty"];
            break;
        case "beginner_intermediate":
            object.icons = ["icon-star", "icon-star", "icon-star-empty", "icon-star-empty", "icon-star-empty"]
            break;
        case "intermediate":
            object.icons = ["icon-star", "icon-star", "icon-star", "icon-star-empty", "icon-star-empty"]
            break;
        case "intermediate_advanced":
            object.icons = ["icon-star", "icon-star", "icon-star", "icon-star", "icon-star-empty"]
            break;
        case "advanced":
            object.icons = ["icon-star","icon-star","icon-star","icon-star","icon-star"]
            break;
    }
    return object;
}

function toCSV(list) {
    return _.reduce(list, function (agg, i) {
        var out = agg;
        if (agg.length > 0) {
            out = agg + ","
        }
        return out + i;
    }, "");
}