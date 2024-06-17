
    override fun onHandshakeResponse(response: UpgradeResponse?) {
    }

    private fun toBasicAuthValue(username: String, password: String): String {
        return "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    }
}