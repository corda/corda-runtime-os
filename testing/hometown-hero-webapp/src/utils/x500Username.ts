export default function extractUsernameFromX500(x500Name: string) {
    var rx = /O=(.*) node,/g;
    var arr = rx.exec(x500Name);
    if (!arr || arr.length < 2) return '';
    return arr[1];
}
