angular.module('ems-filters', []).filter(
  "slotsBySession", function(slots, session) {
    if (session) {
      var durationInMinutes;
      switch(session.object.format) {
        case "lightning-talk":
          durationInMinutes = 10;
          break;
        default:
          durationInMinutes = 60;
          break;
      }
      return _.filter(slots, function(s){
        return s.duration === durationInMinutes;
      });
    }
    return slots;
  }
);