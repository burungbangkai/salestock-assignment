Salestock Shoping Cart Test
==================

This is the solution for Cart REST API . It is implemented on top of play framework 2.5.0 and using Tepkin as mongodb
driver.

This implementation is far for production ready. E.g: the logging mechanism still need to be configured; it only contain one controller, other models only have Dao

This solution were build with micro service and reactive pattern in mind. That is why I pick Tepkin as mongodb driver
and left out the authentication and authentication part to be implemented out by other service.

#Requirements
- MongoDB
- SBT


#Run
```
activator run
```

#Test
```
activator test
```