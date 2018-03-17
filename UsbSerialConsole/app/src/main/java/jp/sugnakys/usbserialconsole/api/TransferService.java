package jp.sugnakys.usbserialconsole.api;

import jp.sugnakys.usbserialconsole.model.AddressResponse;
import jp.sugnakys.usbserialconsole.model.PushTx;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface TransferService {

    @GET("rawaddr/{address}")
    Call<AddressResponse> addressDetails(@Path("address") String address);

    @POST("txs/push")
    Call<String> pushTx(@Body PushTx tx, @Query("token") String token);
}
