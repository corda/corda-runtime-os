
var pendingResults = new Map();

window.setInterval(onPoll, 250)

function onPoll() {
    if (pendingResults.size == 0) {
        return;
    }

    pendingResults.forEach((value, key) => {
        getFlowStatus(value.holdingShortId, key)
    })
}

function getFlowStatus(shortId, requestId) {
    var Http = new XMLHttpRequest();
    var url = 'http://localhost:3000/flow/' + shortId + '/' + requestId;

    Http.open("GET", url, false);
    Http.setRequestHeader("Content-Type", "application/json;charset=UTF-8");
    Http.send();

    if (Http.readyState == 4 && Http.status == 200) {
        var statusUpdate = queueResponse(JSON.parse(Http.responseText))
        if (statusUpdate != null) {
            addStatusUpdate(statusUpdate)
        }
    }
}

function addStatusUpdate(statusUpdate) {
    var id = statusUpdate.clientRequestId;
    var status = statusUpdate.flowStatus;
    var flowId = statusUpdate.flowId;
    var flowResult = "...";

    var gameResult = statusUpdate.flowResult;

    if (gameResult != null) {
        gameResult = JSON.parse(gameResult)
        flowResult = '<code>' + JSON.stringify(gameResult, null, "\t") + '</code>';
    }

    var cols = [
        getCell(id),
        getCell(status),
        getCell(flowId),
        getCell(flowResult)
    ].join('');

    $('#resultTable > tbody:last-child').append('<tr>' + cols + '</tr>');
}

function getCell(v) {
    return "<td>" + v + "</td>"
}

function queueResponse(initialStatus) {
    var requestId = initialStatus.clientRequestId;
    var existingItem = pendingResults.get(requestId);

    // Handle a new item
    var notExisting = !existingItem
    var isRequested = initialStatus.flowStatus == "START_REQUESTED"
    if (notExisting && initialStatus.flowStatus == "START_REQUESTED") {
        pendingResults.set(requestId, initialStatus)
        return initialStatus;
    }

    // Nothing to do if the status has not changed     
    if (existingItem.flowStatus == initialStatus.flowStatus) {
        return null;
    }

    // If the status had changed to completed then we can remove the item
    if (initialStatus.flowStatus == "COMPLETED") {
        pendingResults.delete(requestId)
        return initialStatus;
    }

    // default update
    pendingResults.set(requestId, initialStatus)
    return initialStatus;
}

function getNodeShortId() {
    var selectedName = $("#virtualNode").val();
    return $('#virtualNodeList').find('option[value="' + selectedName + '"]').text()
}

function setOpponent(name) {
    $("#opponent").val(name)
}

function getStartRequest() {
    return {
        shortId: getNodeShortId(),
        requestId: Date.now().toString(),
        flowClass: "net.corda.demo.connectfour.StartConnectFourGameFlow",
        startMessage: '{ "opponentX500Name":"' + $("#opponent").val() + '", "startingSlotPlayed":"' + $("#startingColumn").val() + '"}'
    }
}

function postStartRequest(startRequest) {
    var Http = new XMLHttpRequest();
    var url = 'http://localhost:3000/flow/start/' + startRequest.shortId + '/' + startRequest.requestId + '/' + startRequest.flowClass;

    Http.open("POST", url);
    Http.setRequestHeader("Content-Type", "application/json;charset=UTF-8");

    Http.onreadystatechange = (e) => {
        if (Http.readyState == 4 && Http.status == 200) {
            var startResponse = JSON.parse(Http.responseText)
            if (startResponse.isExistingFlow == false) {
                var statusUpdate = queueResponse(startResponse.flowStatus)
                if (statusUpdate != null) {
                    addStatusUpdate(statusUpdate)
                }
            }
        }

        Http.responseText
    }

    var postData = { requestBody: startRequest.startMessage }

    console.log(postData)
    Http.send(JSON.stringify(postData));
}

function registerChangeEvent() {
    $("#virtualNode").change(function () {
        console.log("changed")

        var id = getNodeShortId()
        console.log("changed " + id)
        console.log($("#virtualNode").val())
        setOpponent("")

        if (id == '244C10730ADF') {
            setOpponent("CN=Alice, O=Alice Corp, L=LDN, C=GB")
            return
        }

        if (id == '2EC4BD692D68') {
            setOpponent("CN=Bob, O=Bob Corp, L=LDN, C=GB")
            return
        }

        setOpponent("...")
    })
}

function registerSubmitEvent() {
    $("#form1").submit(function (event) {
        postStartRequest(getStartRequest())
        event.preventDefault();
    })
}

$(document).ready(function () {
    registerChangeEvent()
    registerSubmitEvent()
});