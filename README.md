Salestock Shoping Cart Test
==================

This is the solution for Cart REST API . It is implemented on top of play framework 2.5.0 and using Tepkin as mongodb
driver.

This implementation is not quite for production ready. E.g: the logging mechanism still need to be configured; it only contain one controller, other models only have Dao or Repo object.

This solution were build with micro service and reactive pattern in mind. That is why I pick Tepkin as mongodb driver
and left out the authentication and authentication part to be implemented by other service. Ideally the item and coupon checking and other management activity should be done by their own service. However, I think its implementation is out of scope of the assignment; thus, the Cart controller is directly using their Repo/DAO object.

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