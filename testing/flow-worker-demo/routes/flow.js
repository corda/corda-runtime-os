var express = require('express');
const { acceptsEncodings } = require('express/lib/request');

var router = express.Router();
var axios = require('axios').default;

const authConfig = {
    auth: {
      username: 'admin',
      password: 'admin'
    }
}

process.env["NODE_TLS_REJECT_UNAUTHORIZED"] = 0;

/* GET home page. */
router.post('/start/:holdershortid/:clientrequestid/:flowclassname', function (req, res, next) {
    var shortId = req.params.holdershortid;
    var requestId = req.params.clientrequestid;
    var flowclassname = req.params.flowclassname;
 
    axios.post(`https://localhost:8888/api/v1/flow/start/${shortId}/${requestId}/${flowclassname}`,req.body, authConfig)
    .then( function(response){
        res.status = response.status;
        res.send(response.data);
    })
    .catch(function(reason){
        res.status=500
        res.send(reason)
    })
});

router.get('/:holdershortid/:clientrequestid/', function (req, res, next) {
    var shortId = req.params.holdershortid;
    var clientrequestid = req.params.clientrequestid;
    
    axios.get(`https://localhost:8888/api/v1/flow/status/${shortId}/${clientrequestid}`, authConfig)
    .then( function(response){
        res.status = response.status;
        res.send(response.data);
    })
    .catch(function(reason){
        res.status=500
        res.send(reason)
    })
});

module.exports = router;