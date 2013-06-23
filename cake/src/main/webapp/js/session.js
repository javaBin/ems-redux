var sessionHelpers = {};

sessionHelpers.mapState = function(state) {
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

sessionHelpers.mapFormat = function(format) {
    var object = {
        name: format
    }
    switch(format) {
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

sessionHelpers.mapLevel = function(level) {
    var object = {
        name: level
    }

    switch (level) {
        case "beginner":
            object.icons = ["icon-star", "icon-star-empty", "icon-star-empty", "icon-star-empty", "icon-star-empty", "icon-star-empty"];
            break;
        case "beginner_intermediate":
            object.icons = ["icon-star", "icon-star", "icon-star-empty", "icon-star-empty", "icon-star-empty", "icon-star-empty"];
            break;
        case "intermediate":
            object.icons = ["icon-star", "icon-star", "icon-star", "icon-star-empty", "icon-star-empty", "icon-star-empty"];
            break;
        case "intermediate_advanced":
            object.icons = ["icon-star", "icon-star", "icon-star", "icon-star", "icon-star-empty", "icon-star-empty"];
            break;
        case "advanced":
            object.icons = ["icon-star", "icon-star", "icon-star", "icon-star", "icon-star", "icon-star-empty"];
            break;
        case "hardcore":
            object.icons = ["icon-star", "icon-star", "icon-star", "icon-star", "icon-star", "icon-star"];
            break;
    }
    return object;
}

sessionHelpers.mapLang = function(lang) {
    var object = {
        name: lang
    }
    switch(lang) {
        case "en":
            object.css = "flag flag-gb";
            object.title = "English";
            break;
        case "no":
        default:
            object.css = "flag flag-no";
            object.title = "Norwegian";
            break;
    }

    return object;
}

sessionHelpers.handleSlot = function(link) {
  if (link) {
    var parts = link.prompt.split("+")
    if (parts.length === 2) {
      var start = moment(parts[0]);
      var end = moment(parts[1]);
      link.parsed = {
        start: start,
        startTime: start.format("HH:mm"),
        end: end,
        endTime: end.format("HH:mm"),
        day: start.format("YYYY-MM-DD")
      }
    }
  }
  return link;
}
