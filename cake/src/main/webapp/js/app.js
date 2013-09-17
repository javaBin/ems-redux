var app = angular.module('app', ['ngRoute','ngSanitize', 'ngCookies', 'ems-filters']).
  config(function ($routeProvider, $httpProvider) {
    $routeProvider.
      when('/', {controller: 'Main', templateUrl: 'fragment/main.html'}).
      when('/about', {controller: 'About', templateUrl: 'fragment/about.html'}).
      when('/events/:slug', {controller: 'SessionList', templateUrl: 'fragment/sessions.html'}).
      when('/assign/:slug', {controller: 'AssignSlots', templateUrl: 'fragment/assign-slots.html'}).
      when('/events/:eventSlug/sessions/:slug', {controller: 'SingleSession', templateUrl: 'fragment/single-session.html'}).
      otherwise({redirectTo: '/'});
    $httpProvider.interceptors.push(function($q) {
      return {
        'request': function(config) {
          if (config) {
            config.url = app.wrapAjax(config.url);
          }
          return config || $q.when(config);
        }
      }});
  }).
  run(function ($http) {
    app.loadRoot($http, function (root) {

    });
  });

app.wrapAjax = function (url) {
  if (url.match(/server/)) {
    var documentLocation = URI(window.location.href);
    var parsedURI = URI(url);
    var actual = parsedURI.is("relative") ? parsedURI.absoluteTo(documentLocation) : parsedURI;
    return URI("ajax").addQuery("href", actual).toString();
  } else {
    return url;
  }
}

app.loadRoot = function ($http, cb) {
  if (!app.root) {
    var root = $('head link[rel="nofollow index"]').attr("href");
    console.log("The configured root is: " + root)

    $http.get(root, {cache: true}).success(function (data) {
      app.root = toCollection(data);
      cb(app.root);
    });
  }
  else {
    cb(app.root);
  }
}

app.controller('LoadEvents', function ($scope, $http) {
  if (!app.events) {
    app.loadRoot($http, function (root) {
      var eventHref = root.findLinkByRel("event collection").href;
      $http.get(eventHref).success(function (data) {
        var events = toCollection(data).mapItems(EmsEvent);
        app.events = events;
        $scope.events = events;
      });
    });
  }
});

app.controller('Navigation', function($scope, $location) {
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
});

app.controller('Login', function ($scope, $rootScope, $http, $cookies, $window) {
  var clearAuthTokens = function() {
    $rootScope.signedIn = false;
    if ($cookies.username) {
      delete $cookies.username
    }
    if ($scope.username) {
      delete $scope.username;
    }
    if ($scope.password) {
      delete $scope.password;
    }
  };

  var signIn = function () {
    if (!$rootScope.signedIn) {
      var postData = "username=" + $scope.username + "&password=" + $scope.password;
      $http({
        url: "login",
        method: "POST",
        headers: {"Content-Type": "application/x-www-form-urlencoded"},
        data: postData
      }).success(function () {
        $rootScope.signedIn = ((typeof $scope.username) !== "undefined");
        console.log("Sucessfully signed in as " + $scope.username);
        //$window.location.reload();
      }).error(function () {
          clearAuthTokens();
        });
    }
  }

  var signOut = function () {
    clearAuthTokens();
    $http.post("logout").success(function () {
      console.log("Successfully logged out");
    }).error(function () {
        console.log("Failed logged out");
    });
  }

  $scope.signIn = signIn;
  $scope.signOut = signOut;
  $scope.username = $cookies.username;
  $rootScope.signedIn = ((typeof $scope.username) !== "undefined");
});

app.controller('Main', function ($scope) {
});

app.controller('About', function ($scope) {
});

app.controller('AssignSlots', function($scope, $routeParams, $http) {
  app.loadRoot($http, function(root) {
    var query = root.findQueryByRel("event by-slug");
    $http.get(query.expand({"slug": $routeParams.slug}), {cache: true}).success(function (eventCollection) {
      var event = EmsEvent(toCollection(eventCollection).headItem());
      var roomLink = event.item.findLinkByRel("room collection");
      var slotLink = event.item.findLinkByRel("slot collection");
      $http.get(slotLink.href).success(function(data){
        var slots = _.sortBy(toCollection(data).mapItems(EmsSlot), "start");
        var groupedSlots = _.groupBy(slots, function(s){
          return s.dayOfYear;
        });
        $scope.grouped = _.reduce(_.keys(groupedSlots), function(acc, key){
          acc[key] = {
            slots: groupedSlots[key],
            day: moment().dayOfYear(key).format("YYYY-MM-DD")
          }
          return acc;
        }, {});
      });
      $http.get(roomLink.href).success(function(data){
        var rooms = toCollection(data).mapItems(EmsRoom);
        $scope.sessionByRoom = function(sessions, room) {
          return _.find(sessions, function(s){
            var roomLink = s.item.findLinkByRel("room item");
            return roomLink ? room.item.href === roomLink.href : false;
          });
        }
        $scope.rooms = rooms;
      });
      $http.get(event.sessions).success(function (data) {
        var sessions = _.filter(toCollection(data).mapItems(EmsSession), function(s) {
          return s.object.state === "approved";
        });
        var groupedSessions = _.reduce(sessions, function(acc, s){
          var assigned = s.item.findLinkByRel("slot item");
          if (assigned) {
            var _arr = acc[assigned.href] || [];
            _arr.push(s);
            acc[assigned.href] = _arr;
          }
          else {
            var unassigned = acc["unassigned"] || [];
            unassigned.push(s);
            acc["unassigned"] = unassigned;
          }
          return acc;

        }, {});
        $scope.sessions = groupedSessions;
        $scope.eventSlug = $routeParams.slug;
      })
      });
    });
});

app.controller('SessionList', function ($scope, $routeParams, $http,$rootScope) {
  $scope.numSessions = 0;
  $scope.showingSessions = 0;
  $scope.usedTags = [];
  app.loadRoot($http, function (root) {
    var query = root.findQueryByRel("event by-slug");
    $http.get(query.expand({"slug": $routeParams.slug})).success(function (eventCollection) {
      var event = EmsEvent(toCollection(eventCollection).headItem());
      $http.get(event.sessions).success(function (data) {
        $scope.sessions = toCollection(data).mapItems(EmsSession);
        $scope.numSessions = $scope.sessions.length;
        $rootScope.allTags = _.uniq(_.flatten(_.map($scope.sessions,function(session) { return session.object.tags; })));
        $scope.usedTags = _.map($rootScope.allTags,
          function(tag) { return {name: tag, selected: false }});
        $scope.showingSessions = $scope.numSessions;
        $scope.filteredSessions = $scope.sessions.slice(0);
        $scope.name = event.object.name;
        $scope.eventSlug = event.object.slug;
      });
    });
  });

  $scope.updateAllSelectedTags = function(updateTo) {
    _.each($scope.usedTags,function(usedTag) {
      usedTag.selected = updateTo;
    });
    $scope.filterChanged();
  }

  $scope.sortSessionBy = "speaker";
  $scope.filterValues = {
    title : "",
    speakers: "",
    presType: "both"
  };

  $scope.filterChanged = function() {
    var usedT = _.pluck(_.filter($scope.usedTags,
            function(usedTag) {
              return usedTag.selected;
            }), "name");

    $scope.filteredSessions = _.filter($scope.sessions,function(session) {
      return (
        (($scope.filterValues.title === "") || (session.object.title.toLowerCase().indexOf($scope.filterValues.title.toLowerCase()) !== -1)) &&
        (($scope.filterValues.speakers === "") || (session.speakersAsString.toLowerCase().indexOf($scope.filterValues.speakers.toLowerCase()) !== -1)) &&
        (($scope.filterValues.presType === "both") || ($scope.filterValues.presType === session.format.name)) &&
        (!session.object.tags || (_.intersection(session.object.tags,usedT).length === usedT.length))
        );
    });
    $scope.showingSessions = $scope.filteredSessions.length;
  }

  $scope.clearFilters = function() {
    $scope.filterValues = {
      title : "",
      speakers: "",
      presType: "both"
    };
    _.each($scope.usedTags,function(usedTag) {
      usedTag.selected = false;
    });
    $scope.filterChanged();
  }

  $scope.filterPresType = function(val) {
    console.log(val);
    $scope.filterValues.presType = val;
    $scope.filterChanged();
  }

  $scope.orderSessionsFunction = function(asession) {
    if ($scope.sortSessionBy === "speaker") {
      return asession.speakersAsString;
    } else {
      return asession.object.title;
    }
  };


});

app.controller('SingleSession', function ($scope, $routeParams, $http, $window,$rootScope) {
  $scope.showSuccess = false;
  var eventSlug = $routeParams.eventSlug;
  var slug = $routeParams.slug;
  app.loadRoot($http, function (root) {
    $http.get(root.findQueryByRel("event by-slug").expand({"slug": eventSlug}), {cache: true}).success(function (eventCollection) {
      var event = EmsEvent(toCollection(eventCollection).headItem());
      $http.get(event.item.findLinkByRel("slot collection").href).success(function (data) {
        var slots = _.sortBy(toCollection(data).mapItems(EmsSlot), "start");
        $scope.slotsBySession = function (session) {
          if (session) {
            var durationInMinutes;
            switch (session.object.format) {
              case "lightning-talk":
                durationInMinutes = 10;
                break;
              default:
                durationInMinutes = 60;
                break;
            }
            return _.filter(slots, function (s) {
              return s.duration === durationInMinutes;
            });
          }
          return slots;
        };
      });

      $http.get(event.item.findLinkByRel("room collection").href).success(function (data) {
        $scope.rooms = toCollection(data).mapItems(EmsRoom);
        console.log($scope.rooms);
        if ($scope.session) {
          console.log($scope.session.room.href)
        }
      });
    });

    var query = root.findQueryByRel("event session by-slug");
    if (query) {
      var url = query.expand({"event-slug": eventSlug, "session-slug": slug});
      $http.get(url).success(function (sessionCollection,status, headers) {
        var collection = toCollection(sessionCollection);
        var session = EmsSession(collection.headItem());
        session.lastModified = headers("last-modified");
        var speakerLink = session.item.findLinkByRel("speaker collection");
        $http.get(speakerLink.href).success(function (speakerCollection) {
          $scope.speakers = toCollection(speakerCollection).mapItems(EmsSpeaker);
        });
        $scope.session = session;
        var myTags = $("#myTags");
        var avTags = [];
        if ($rootScope.allTags) {
          avTags = $rootScope.allTags;
        }
        if (collection.collection.template) {
          $scope.states = collection.collection.template.get("state").options;
        }
        $scope.signedIn = $rootScope.signedIn;
        myTags.tagit({ availableTags: avTags,autocomplete: {delay: 0, minLength: 1}});
        _.each($scope.session.object.tags,function(atag) {
          myTags.tagit("createTag",atag);
        });
      });
      }
      else {
        console.log("WARN: missing session slug query!")
      }
  });


  $scope.updateTags = function() {
    $scope.showSuccess = false;
    var updatedTags = $("#myTags").tagit("assignedTags");
    $scope.session.object.tags = updatedTags;
    var data = _.reduce(updatedTags, function(agg, e) {
      return agg + (agg.length > 0 ? "&" : "") + "tag=" + e;
    }, "");
    app.updateTarget($http, $scope, $window, data);
  }

  $scope.updateRoom = function() {
    var href = $scope.selectedRoom;
    app.updateTarget($http, $scope, $window, "room=" + href);
  }

  $scope.updateSlot = function() {
    var href = $scope.selectedSlot;
    app.updateTarget($http, $scope, $window, "slot=" + href);
  }

  $scope.updateSession = function() {
    var session = $scope.session;
    var template = session.object.toTemplate().toJSON();
    $http({
      url: session.item.href,
      method: "PUT",
      headers: {"Content-Type": "application/vnd.collection+json", "If-Unmodified-Since": session.lastModified},
      data: template
    }).success(function () {
        setTimeout(function(){
          $window.location.reload();
        }, 1000);
      }).error(function(e) {
        console.log(e);
      });

  }
  $scope.publish = function() {
    var session = $scope.session;
    app.publish($http, $scope, $window, session.item.findLinkByRel("publish").href, [session])
  }

});

app.updateTarget = function($http, $scope, $window, data) {
    app.postFormData($http, $scope, $window, $scope.session.item.href, data);
}

app.postFormData = function($http, $scope, $window, href, data) {
  $http({
    url: href,
    method: "POST",
    headers: {"Content-Type": "application/x-www-form-urlencoded", "If-Unmodified-Since": $scope.session.lastModified},
    data: data
  }).success(function () {
      $scope.showSuccess = true;
      setTimeout(function(){
        $window.location.reload();
      }, 1000);

    }).error(function(e) {
      console.log(e);
    });
}

app.publish = function($http, $scope, $window, href, sessionsToPublish) {
  $http({
    url: href,
    method: "POST",
    headers: {"Content-Type": "text/uri-list"},
    data: _.reduce(sessionsToPublish, function(agg, s){
      return agg + s.item.href + "\r\n";
    }, "")
  }).success(function () {
      $scope.showSuccess = true;
      setTimeout(function(){
        $window.location.reload();
      }, 2000);

    }).error(function(e) {
      console.log(e);
    });
}