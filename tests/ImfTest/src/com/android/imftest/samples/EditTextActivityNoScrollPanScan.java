package com.android.imftest.samples;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ScrollView;

import com.android.internal.R;

public class EditTextActivityNoScrollPanScan extends Activity 
{
   @Override
   public void onCreate(Bundle savedInstanceState) 
   {
       super.onCreate(savedInstanceState);
                
       LinearLayout layout = new LinearLayout(this);
       layout.setOrientation(LinearLayout.VERTICAL);
       
       String string = new String();
       for (int i=0; i<9; i++) 
       {
           final EditText editText = new EditText(this);
           editText.setText(string.valueOf(i));
           layout.addView(editText);
       }
       setContentView(layout);
       this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
    }  
}
