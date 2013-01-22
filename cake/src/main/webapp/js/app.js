var app = {};
app.date = {};

angular.module('app', []).
    config(function($routeProvider) {
        $routeProvider.
            when('/', {controller:app.Main, templateUrl: 'about.html'}).
            when('/events/:slug', {controller:app.SessionList, templateUrl:'sessions.html'}).
            when('/events/:eventSlug/sessions/:slug', {controller:app.SingleSession, templateUrl:'single-session.html'})./*.
            when('/edit/:projectId', {controller:EditCtrl, templateUrl:'detail.html'}).
            when('/new', {controller:CreateCtrl, templateUrl:'detail.html'}).*/
            otherwise({redirectTo:'/'});
    }).run(function($http) {
        app.loadRoot($http, function(root) {

        });
    });

app.date.parse = function(dateString) {
    return Date.parseExact(dateString, 'yyyy-MM-ddTHH:mm:ssZ');
}

app.date.toString = function(date) {
    return date.toString('yyyy-MM-ddTHH:mm:ssZ');
}

app.wrapAjax = function(url) {
    return "ajax?href=" + url;
}

app.loadRoot = function($http, cb) {
    if (!app.root) {
        var root = $('head link[rel="nofollow index"]').attr("href");
        console.log("The configured root is: " + root)

        $http.get(app.wrapAjax(root)).success(function(data) {
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
    if (!app.events) {
        app.loadRoot($http, function(root) {
            var eventHref = root.findLinkByRel("event collection").href;
            $http.get(app.wrapAjax(eventHref)).success(function(data) {
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
        var query = root.findQueryByRel("event by-slug");
        $http.get(app.wrapAjax(query.expand({"slug": $routeParams.slug}))).success(function(eventCollection){
            var event = app.mapEvent(_.head(fromObject(eventCollection).collection.items));
            $http.get(app.wrapAjax(event.sessions)).success(function(data) {
                var sessions = _.map(fromObject(data).collection.items, app.mapSession);
                $scope.sessions = sessions;
                $scope.name = event.data.name;
                $scope.eventSlug = event.data.slug;
            });
        });
    });
}

app.SingleSession = function($scope, $routeParams, $http) {
    var eventSlug = $routeParams.eventSlug;
    var slug = $routeParams.slug;
    app.loadRoot($http, function(root) {
        var query = root.findQueryByRel("event by-slug");
        $http.get(app.wrapAjax(query.expand({"slug": eventSlug}))).success(function(eventCollection){
            var event = app.mapEvent(_.head(fromObject(eventCollection).collection.items));
            var query = expandQuery({href: event.sessions}, {"slug": slug});
            $http.get(app.wrapAjax(query)).success(function(sessionCollection){
                var session = app.mapSession(_.head(fromObject(sessionCollection).collection.items));
                $scope.session = session;
            });
        });
    });
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
    i.state = sessionHelpers.mapState(i.data.state);
    i.format = sessionHelpers.mapFormat(i.data.format);
    i.level = sessionHelpers.mapLevel(i.data.level);
    i.speakers = findLinksByRel(i, "speaker item");
    i.speakersAsString = toCSV(i.speakers.map(function(i){return i.prompt}));
    return i;
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