var app = {};

angular.module('app', ['ngSanitize', 'ngCookies']).
  config(function ($routeProvider) {
    $routeProvider.
      when('/', {controller: app.Main, templateUrl: 'main.html'}).
      when('/about', {controller: app.About, templateUrl: 'about.html'}).
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
  var actual = parsedURI.is("relative") ? parsedURI.absoluteTo(documentLocation) : parsedURI;
  return "ajax?href=" + actual;
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

app.Navigation = function($scope, $location) {
  $scope.isRoot = function() {
    return $location.path() === '/' ? "active" : "inactive";
  }
  $scope.isAbout = function() {
    return $location.path() === '/about' ? "active" : "inactive";
  }
  $scope.isEventOrSession = function() {
    return $location.path().indexOf('/events/') !== -1 ? "active" : "inactive";
  }
  $scope.Login = app.Login;
  $scope.LoadEvents = app.LoadEvents;
}

app.Login = function ($scope, $http, $cookies, $window) {
  var signIn = function () {
    if (!$scope.signedIn) {
      var username = $("#username").val();
      var password = $("#password").val();
      var postData = "username=" + username + "&password=" + password;
      $http({
        url: "login",
        method: "POST",
        headers: {"Content-Type": "application/x-www-form-urlencoded"},
        data: postData
      }).success(function () {
        $scope.signedIn = ((typeof $scope.username) !== "undefined");
        $window.location.reload();
      }).error(function () {
          if ($cookies.username) {
            delete $cookies.username
          }
        });
    }
  }

  var signOut = function () {
    $scope.signedIn = false;
    if ($cookies.username) {
      delete $cookies.username;
    }
    $http.post("logout").success(function () {
      console.log("Successfully logged out");
    }).error(function () {
        console.log("Failed logged out");
      });
  }

  $scope.signIn = signIn;
  $scope.signOut = signOut;
  $scope.username = $cookies.username;
  $scope.signedIn = ((typeof $scope.username) !== "undefined");
}

app.Main = function ($scope) {
}

app.About = function ($scope) {
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
          console.log(session);
          var speakerLink = session.item.findLinkByRel("speaker collection");
          $http.get(app.wrapAjax(speakerLink.href)).success(function (speakerCollection) {
            $scope.speakers = toCollection(speakerCollection).mapItems(EmsSpeaker);
          });
          $scope.session = session;
        });
      }
      else {
        console.log("WARN: missing session slug query!")
      }
    });
  });
}
