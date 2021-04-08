package api.samples.producerconsumer.subscription


interface LifeCycle  {
   fun start()
   fun cancel()

   //i suspect you may not want these two but if theres scenarios where we are going to start/stop/start then having these might make sense?
   fun pause()
   fun play()
}

