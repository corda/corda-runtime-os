package net.corda.application.banner

import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@ServiceRanking(Int.MIN_VALUE)
@Component(service = [StartupBanner::class])
class CordaOsBanner : StartupBanner {
    override fun get(name: String, version: String) =
        """
    ______               __      
   / ____/     _________/ /___ _ 
  / /     __  / ___/ __  / __ `/ 
 / /___  /_/ / /  / /_/ / /_/ /  
 \____/     /_/   \__,_/\__,_/ 
 --- $name ($version) ---
"""
}