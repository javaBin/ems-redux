var app = {};

angular.module('app', ['ngSanitize']).
    config(function ($routeProvider) {
      $routeProvider.
          when('/', {controller: app.Main, templateUrl: 'about.html'}).
          when('/events/:slug', {controller: app.SessionList, templateUrl: 'sessions.html'}).
          when('/events/:eventSlug/sessions/:slug', {controller: app.SingleSession, templateUrl: 'single-session.html'}).
          otherwise({redirectTo: '/'});
    }).
    run(function ($http) {
      app.loadRoot($http, function (root) {

      });
    });

app.wrapAjax = function (url) {
  var documentLocation = URI(window.location.href);
  var parsedURI = URI(url);
  var sameHost = parsedURI.is("relative") || (documentLocation.host() === parsedURI.host() && documentLocation.port() === parsedURI.port())
  return !sameHost ? ("ajax?href=" + url) : url;
}

app.loadRoot = function ($http, cb) {
  if (!app.root) {
    var root = $('head link[rel="nofollow index"]').attr("href");
    console.log("The configured root is: " + root)

    $http.get(app.wrapAjax(root), {cache: true}).success(function (data) {
      app.root = toCollection(data);
      cb(app.root);
    });
  }
  else {
    cb(app.root);
  }
}

app.LoadEvents = function ($scope, $http) {
  if (!app.events) {
    app.loadRoot($http, function (root) {
      var eventHref = root.findLinkByRel("event collection").href;
      $http.get(app.wrapAjax(eventHref), {cache: true}).success(function (data) {
        var events = toCollection(data).mapItems(EmsEvent);
        app.events = events;
        $scope.events = events;
      });
    });
  }
}

app.Main = function ($scope, $http) {
  console.log("Main called" + $scope);
}

app.SessionList = function ($scope, $routeParams, $http) {
  app.loadRoot($http, function (root) {
    var query = root.findQueryByRel("event by-slug");
    $http.get(app.wrapAjax(query.expand({"slug": $routeParams.slug})), {cache: true}).success(function (eventCollection) {
      var event = EmsEvent(toCollection(eventCollection).headItem());
      $http.get(app.wrapAjax(event.sessions)).success(function (data) {
        $scope.sessions = toCollection(data).mapItems(EmsSession);
        $scope.name = event.object.name;
        $scope.eventSlug = event.object.slug;
      });
    });
  });
}

app.SingleSession = function ($scope, $routeParams, $http) {
  var eventSlug = $routeParams.eventSlug;
  var slug = $routeParams.slug;
  app.loadRoot($http, function (root) {
    var query = root.findQueryByRel("event by-slug");
    $http.get(app.wrapAjax(query.expand({"slug": eventSlug})), {cache: true}).success(function (eventCollection) {
      var query = toCollection(eventCollection).findQueryByRel("session by-slug");
      if (query) {
        $http.get(app.wrapAjax(query.expand({"slug": slug}, {cache: true}))).success(function (sessionCollection) {
          var session = EmsSession(toCollection(sessionCollection).headItem());
          var speakerLink = session.item.findLinkByRel("speaker collection");
          $http.get(app.wrapAjax(speakerLink.href)).success(function (speakerCollection) {
            $scope.speakers = toCollection(speakerCollection).mapItems(EmsSpeaker);
          });
          $scope.session = session;
        });
      }
    });
  });
}
